const path = require('node:path');
const ReactFlightWebpackPlugin = require('react-server-dom-webpack/plugin');
module.exports = {
  mode: 'development',
  context: __dirname,
  entry: './browser-entry.js',
  output: {path: path.join(__dirname, 'target/site'), filename: 'application.js', publicPath: '/assets/', clean: true},
  devtool: 'source-map',
  plugins: [new ReactFlightWebpackPlugin({isServer: false,
    clientReferences: [path.join(__dirname, 'jx-client.js'), path.join(__dirname, 'reference-client.js')]})],
  performance: {hints: false}
};
