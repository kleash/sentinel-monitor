const target = process.env.API_PROXY_TARGET || 'http://localhost:8080';

module.exports = [
  {
    context: ['/api', '/actuator'],
    target,
    secure: false,
    changeOrigin: true,
    pathRewrite: { '^/api': '' },
    logLevel: 'warn'
  }
];
