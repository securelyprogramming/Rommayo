FROM node:24-alpine
WORKDIR /app
COPY web ./web
COPY shared ./shared
COPY assets/icon.png ./assets/icon.png
COPY desktop/policy.cjs ./desktop/policy.cjs
USER node
ENV HOST=0.0.0.0 PORT=8787
EXPOSE 8787
CMD ["node", "web/server.cjs"]
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 CMD node -e "fetch('http://127.0.0.1:'+(process.env.PORT||8787)+'/__rommayo/config').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
