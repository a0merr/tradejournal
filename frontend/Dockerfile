# Dev-server image: builds the app and serves it via Vite with the /api proxy.
# (For production you'd build static assets and serve them behind the API / a CDN.)
FROM node:20-alpine
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm install
COPY . .
EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host"]
