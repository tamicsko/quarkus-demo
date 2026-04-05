# Stage 1: Build Angular app (Red Hat UBI Node.js)
FROM registry.access.redhat.com/ubi9/nodejs-22:1 AS build
ENV NODE_OPTIONS="--max-old-space-size=512"
USER 0
RUN mkdir -p /app && chown 1001:0 /app
USER 1001
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npx ng build --configuration production

# Stage 2: Serve with Red Hat UBI nginx (non-root, OpenShift kompatibilis)
FROM registry.access.redhat.com/ubi9/nginx-124:1
COPY --from=build /app/dist/frontend/browser /opt/app-root/src
COPY nginx.conf /opt/app-root/etc/nginx.default.d/app.conf
EXPOSE 8080
CMD ["nginx", "-g", "daemon off;"]
