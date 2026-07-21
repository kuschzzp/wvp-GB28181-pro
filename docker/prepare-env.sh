#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${1:-${SCRIPT_DIR}/.env}"

REQUIRED_SECRET_BYTES=32
SECRET_NAMES=("API_KEY_PEPPER" "MEDIA_HOOK_SECRET_PEPPER")

die() {
  echo "ERROR: $*" >&2
  exit 1
}

generate_secret() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 48
    return
  fi

  if command -v python3 >/dev/null 2>&1; then
    python3 -c 'import secrets; print(secrets.token_urlsafe(48))'
    return
  fi

  die "openssl or python3 is required to generate missing secrets"
}

read_env_value() {
  local name="$1"

  awk -v key="${name}" '
    $0 ~ "^[[:space:]]*(export[[:space:]]+)?" key "[[:space:]]*=" {
      line = $0
      sub(/^[[:space:]]*export[[:space:]]+/, "", line)
      sub("^[[:space:]]*" key "[[:space:]]*=", "", line)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", line)

      if (line ~ /^".*"$/ || line ~ /^\047.*\047$/) {
        sub(/^["\047]/, "", line)
        sub(/["\047]$/, "", line)
      }

      print line
      exit
    }
  ' "${ENV_FILE}"
}

has_env_key() {
  local name="$1"
  grep -Eq "^[[:space:]]*(export[[:space:]]+)?${name}[[:space:]]*=" "${ENV_FILE}"
}

write_env_value() {
  local name="$1"
  local value="$2"

  if has_env_key "${name}"; then
    local tmp_file
    tmp_file="$(mktemp "${ENV_FILE}.tmp.XXXXXX")"

    awk -v key="${name}" -v replacement="${name}=${value}" '
      BEGIN { updated = 0 }

      $0 ~ "^[[:space:]]*(export[[:space:]]+)?" key "[[:space:]]*=" && updated == 0 {
        print replacement
        updated = 1
        next
      }

      { print }
    ' "${ENV_FILE}" > "${tmp_file}"

    mv "${tmp_file}" "${ENV_FILE}"
  else
    printf "\n%s=%s\n" "${name}" "${value}" >> "${ENV_FILE}"
  fi
}

ensure_secret() {
  local name="$1"
  local value
  value="$(read_env_value "${name}")"

  if [ -n "${value}" ]; then
    echo "Keep existing ${name}."
    return
  fi

  value="$(generate_secret)"
  write_env_value "${name}" "${value}"
  echo "Generated ${name} and wrote it to ${ENV_FILE}."
}

validate_secret_length() {
  local name="$1"
  local value
  local byte_len

  value="$(read_env_value "${name}")"
  byte_len="$(printf '%s' "${value}" | wc -c | tr -d ' ')"

  if [ "${byte_len}" -lt "${REQUIRED_SECRET_BYTES}" ]; then
    die "${name} must be at least ${REQUIRED_SECRET_BYTES} bytes"
  fi
}

[ -f "${ENV_FILE}" ] || die "env file not found: ${ENV_FILE}"

for name in "${SECRET_NAMES[@]}"; do
  ensure_secret "${name}"
done

for name in "${SECRET_NAMES[@]}"; do
  validate_secret_length "${name}"
done

api_key_pepper="$(read_env_value "API_KEY_PEPPER")"
media_hook_secret_pepper="$(read_env_value "MEDIA_HOOK_SECRET_PEPPER")"

if [ "${api_key_pepper}" = "${media_hook_secret_pepper}" ]; then
  die "API_KEY_PEPPER and MEDIA_HOOK_SECRET_PEPPER must be different"
fi

echo "Docker env secrets are ready."
