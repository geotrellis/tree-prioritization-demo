"use strict";

var Webpack = require('webpack'),
    glob = require('glob'),
    path = require('path'),
    _ = require('lodash'),
    BundleTracker = require('webpack-bundle-tracker'),
    ExtractTextPlugin = require("extract-text-webpack-plugin"),
    HtmlWebpackPlugin = require('html-webpack-plugin'),
    autoprefixer = require('autoprefixer');

var outputDir = __dirname + '/dist';

function getAliases() {
    var aliases = {
            'modeling': './js/modeling'
        };
    return _.merge(aliases, shimmed);
}

var shimmed = {
    typeahead: '../shim/typeahead.jquery.js',
    bootstrap: '../shim/bootstrap.js',
    'bootstrap-slider': '../shim/bootstrap-slider.js'
};

module.exports = {
    entry: {
        demo: './js/modeling/modeling.js'
    },
    output: {
        filename: '[name].js',
        path: outputDir,
        sourceMapFilename: '[file].map'
    },
    module: {
        loaders: [{
            include: [shimmed["bootstrap-slider"]],
            loader: "imports?bootstrap"
        }, {
            test: /\.scss$/,
            loader: ExtractTextPlugin.extract(['css', 'postcss-loader', 'sass'], {extract: true})
        }, {
            test: /\.woff($|\?)|\.woff2($|\?)|\.ttf($|\?)|\.eot($|\?)|\.svg($|\?)/,
            loader: 'url'
        }, {
            test: /\.handlebars$/,
            loader: "handlebars-loader"
        }, {
            test: /\.(jpg|png|gif)$/,
            loader: 'url?limit=25000'
        }]
    },
    resolve: {
        alias: getAliases(),
        root: ["./js/vendor", "node_modules", "./assets"]
    },
    resolveLoader: {
        root: __dirname + "/node_modules"
    },
    plugins: [
        // Provide jquery and Leaflet as global variables, which gets rid of
        // most of our shimming needs
        // NOTE: the test configuration relies on this being the first plugin
        new Webpack.ProvidePlugin({
            jQuery: "jquery",
            "window.jQuery": "jquery",
            L: "leaflet",
            toastr: "toastr"
        }),
        new ExtractTextPlugin('css/main.css', {allChunks: true}),
        new BundleTracker({path: outputDir, filename: 'webpack-stats.json'}),
        new HtmlWebpackPlugin({
            title: 'Tree Planting Site Prioritization',
            filename: 'index.html',
            inject : 'body',
            // The template contains underscore templates that are expanded by JS code.
            // But the default HTML loader tries to expand the templates at load time, and crashes.
            // Use the Handlebars loader instead as it ignores the underscore template syntax.
            template: 'template.handlebars'
        })
    ],
    postcss: function () {
        return [autoprefixer];
    }
};
