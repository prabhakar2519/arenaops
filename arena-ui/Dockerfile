FROM node:20-slim AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist/arena-ui/browser /usr/share/nginx/html
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY docker/40-arena-config.sh /docker-entrypoint.d/40-arena-config.sh
RUN chmod +x /docker-entrypoint.d/40-arena-config.sh
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
