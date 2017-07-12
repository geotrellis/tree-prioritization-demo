FROM nginx:1.10

COPY srv/dist /srv/dist/
RUN mkdir -p /etc/nginx/includes \
	&& chown nginx:nginx -R /srv/dist/

COPY etc/nginx/nginx.conf /etc/nginx/nginx.conf
COPY etc/nginx/includes/* /etc/nginx/includes/
COPY etc/nginx/conf.d/* /etc/nginx/conf.d/
