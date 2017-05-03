"use strict";

var _ = require('lodash');

var presets = {
    'high-population-low-canopy': {
        'us-census-population-density-30m-epsg3857': 2,
        'nlcd-2011-canopy-tms-epsg3857': -2
    },
    'low-income-low-vacancy': {
        'us-census-housing-vacancy-30m-epsg3857': -2,
        'us-census-median-household-income-tms-epsg3857': -2
    },
    'low-income-high-impervious': {
        'us-census-median-household-income-tms-epsg3857': -2,
        'nlcd-2011-impervious-tms-epsg3857': 2
    }
};

function get(presetId) {
    var preset = presets[presetId];
    if (preset) {
        return {
            activeVariables: _.keys(preset),
            weights: {
                variables: preset
            }
        };
    } else {
        return {};
    }
}

function match(params) {
    // Find preset matching given params
    var vars = _.pick(params.weights.variables, params.activeVariables);
    return _.findKey(presets, _.partial(_.isEqual, vars));
}

module.exports = {
    get: get,
    match: match
};
