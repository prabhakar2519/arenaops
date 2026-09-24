FROM node:20-slim AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist/arena-ui/browser /usr/share/nginx/html
RUN printf '%s\n' \
  'server {' \
  '  listen 80;' \
  '  server_name localhost;' \
  '  root /usr/share/nginx/html;' \
  '  index index.html;' \
  '  location / { try_files $uri $uri/ /index.html; }' \
  '  location /api/ { proxy_pass http://arena-login:7700; proxy_set_header Host $host; proxy_set_header X-Forwarded-Proto $scheme; }' \
  '  location /oauth2/ { proxy_pass http://arena-login:7700; proxy_set_header Host $host; proxy_set_header X-Forwarded-Proto $scheme; }' \
  '  location /logout { proxy_pass http://arena-login:7700; proxy_set_header Host $host; proxy_set_header X-Forwarded-Proto $scheme; }' \
  '  location /auth/ { proxy_pass http://keycloak:8080; proxy_set_header Host $host; proxy_set_header X-Forwarded-Proto $scheme; proxy_set_header X-Forwarded-Host $host; }' \
  '}' > /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
