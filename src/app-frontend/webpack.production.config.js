"use strict";

// Note: this has not been tested for the otm-modeling standalone demo.
// It is essentially OTM's version of this file, included for its 
// possible usefulness in creating a production version of the demo.

var webpack = require('webpack'),
    config = require('./webpack.common.config.js');

config.output.filename = '[name]-[chunkhash].js';

config.devtool = 'source-map';

config.plugins.concat([
    new webpack.optimize.UglifyJsPlugin({
        mangle: {
            except: ['otm', 'google']
        }
    }),
    new webpack.optimize.OccurrenceOrderPlugin()
]);

//config.output.publicPath = '/static/';

module.exports = config;
