"use strict";

var webpack = require('webpack'),
    config = require('./webpack.common.config.js'),

    host = process.env.WEBPACK_DEV_SERVER || 'http://localhost:8286/';

// Add webpack-dev-server to the entry bundle
config.entry['demo'] = [
    config.entry['demo'],
    'webpack-dev-server/client?' + host,
    'webpack/hot/dev-server'];

config.output.publicPath = host;
config.output.pathInfo = true;

config.debug = true;

config.devtool = 'eval';

config.plugins = config.plugins.concat([
    new webpack.HotModuleReplacementPlugin()
]);

config.watchOptions = {
    poll: 1000
};

module.exports = config;
