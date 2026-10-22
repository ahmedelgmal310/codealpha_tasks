# Nginx web server running as a non-root user.
# nginx-unprivileged listens on 8080 and keeps its pid/temp files in /tmp,
# so the container can also run with a read-only root filesystem.
FROM nginxinc/nginx-unprivileged:1.28-alpine

LABEL org.opencontainers.image.title="codealpha-web-server" \
      org.opencontainers.image.description="Static website served by Nginx inside Docker" \
      org.opencontainers.image.authors="Ahmed Elgammal" \
      org.opencontainers.image.version="1.0.0"

# Site files stay owned by root, so the nginx user can read them but not change them
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY site/ /usr/share/nginx/html/

USER 101

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 \
  CMD ["wget", "-q", "-O", "/dev/null", "http://127.0.0.1:8080/health"]
