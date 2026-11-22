# syntax=docker/dockerfile:1
FROM postgres:16-bookworm AS postgres
FROM nginx:1.28-bookworm AS nginx

FROM backend
USER root

COPY --from=postgres /usr/local/share/keyrings/postgres.gpg.asc /usr/local/share/keyrings/
COPY --from=postgres /usr/local/bin/docker-entrypoint.sh /usr/local/bin/postgres-entrypoint
COPY --from=postgres /usr/local/bin/gosu /usr/local/bin/gosu
COPY --from=nginx /usr/sbin/nginx /usr/sbin/nginx
COPY --from=nginx /etc/nginx/nginx.conf /etc/nginx/mime.types /etc/nginx/
COPY --from=frontend /etc/nginx/conf.d/default.conf /etc/nginx/conf.d/default.conf
COPY --from=frontend /usr/share/nginx/html /usr/share/nginx/html

RUN set -eux; \
    echo 'deb [signed-by=/usr/local/share/keyrings/postgres.gpg.asc] https://apt.postgresql.org/pub/repos/apt bookworm-pgdg main' > /etc/apt/sources.list.d/postgres.list; \
    apt-get update; \
    apt-get install -y --no-install-recommends postgresql-common; \
    sed -i 's/^#create_main_cluster .*/create_main_cluster = false/' /etc/postgresql-common/createcluster.conf; \
    apt-get install -y --no-install-recommends postgresql-16 tini; \
    rm -rf /var/lib/apt/lists/*; \
    useradd --system --no-create-home --user-group nginx; \
    install -d /docker-entrypoint-initdb.d /var/cache/nginx /var/log/nginx; \
    ln -s /dev/stdout /var/log/nginx/access.log; \
    ln -s /dev/stderr /var/log/nginx/error.log; \
    nginx -t

COPY --chmod=755 start.sh /usr/local/bin/whznt-start

ENV LANG=C.UTF-8 \
    PATH="/usr/lib/postgresql/16/bin:$PATH" \
    PGDATA=/var/lib/postgresql/data/pgdata \
    SPRING_CONFIG_IMPORT=optional:file:/run/whznt/config/ai.properties \
    WHZNT_RECHARGE_QR_FILE=/run/whznt/config/recharge-qr.png
VOLUME ["/var/lib/postgresql/data", "/var/lib/whznt"]
EXPOSE 80
HEALTHCHECK --interval=5s --timeout=4s --retries=12 --start-period=15s \
    CMD ["node", "-e", "fetch('http://127.0.0.1/api/auth/csrf').then(r => process.exit(r.ok ? 0 : 1)).catch(() => process.exit(1))"]
ENTRYPOINT ["/usr/bin/tini", "--", "/usr/local/bin/whznt-start"]
