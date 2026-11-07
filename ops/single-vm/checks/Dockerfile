FROM postgres:18.6
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    age awscli caddy msmtp python3-aiosmtpd systemd shellcheck && rm -rf /var/lib/apt/lists/*
