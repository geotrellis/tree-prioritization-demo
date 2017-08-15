"use strict";

var $ = require('jquery'),
    _ = require('lodash'),
    L = require('leaflet'),
    Bacon = require('baconjs'),
    R = require('ramda'),
    slider = require('bootstrap-slider'),
    controls = require('./controls.js'),
    dropdowns = require('./prioritizationDropdowns.js'),
    presets = require('./prioritizationPresets.js'),
    locationMasks = require('./locationMasks.js');

var dom = {
    sidebar: '#sidebar-prioritization',
    layerSections: '#layers .panel-collapse',
    sliders: 'input.slider',
    layerOptions: '#map .options',
    geocode: 'div.geocode',
    expandedLayerSections: '#layers .panel-collapse.in',
    toggleVariable: '#layers input[data-toggle-variable]',
    categorySliders: '#layers input.slider[data-category]',
    weightDropdowns: '#layers .weight-dropdown',
    polarityDropdowns: '#layers .polarity-dropdown',
    presetsDropdown: '#layers .presets-dropdown',
    presetsDropdownLabel: '#presets-dropdown-label .dropdown-label',
    transparencySlider: '.slider.js-transparency',
    priorityThresholdSlider: '.slider.js-priority-threshold',
    rasterMaskCheckboxes: '.js-raster-mask input:checkbox',
    vectorMaskCheckboxes: '.js-vector-mask input:checkbox'
};

var initialized = false,
    _initialParams,
    _urls = null,
    _priorityLayer = null,
    _loadingControl = null,
    _legendControl = null,
    _bounds = null,
    _numBreaks = 10,
    _map = null;

function init(options) {
    _urls = options.urls;
    _bounds = options.instanceBounds;

    _loadingControl = new controls.LoadingControl();
    _loadingControl.hide();

    _legendControl = new controls.LegendControl();

    _map = options.map;

    _map.addControl(_loadingControl);
    _map.addControl(_legendControl);

    _initialParams = getParamsFromUi();

    dropdowns.init();

    // Prevent dragging sliders in the options dialog from dragging the map.
    $(dom.layerOptions).on('mouseover', function() { _map.dragging.disable(); });
    $(dom.layerOptions).on('mouseout', function() { _map.dragging.enable(); });

    // Prevent dragging and double-clicking in the geocode text box from moving the map.
    $(dom.geocode).on('mouseover', function() { _map.dragging.disable(); _map.doubleClickZoom.disable(); });
    $(dom.geocode).on('mouseout', function() { _map.dragging.enable(); _map.doubleClickZoom.enable(); });

    var locationMaskChangedStream = locationMasks.init(options),
        boundsChangedStream = new Bacon.Bus(),
        setPresetBus = new Bacon.Bus(),
        parameterChangedStream = Bacon.mergeAll(
            initDropdownStream(dom.weightDropdowns),
            initDropdownStream(dom.polarityDropdowns),
            initPriorityThresholdChangedStream(),
            $(dom.toggleVariable).asEventStream('change'),
            $(dom.rasterMaskCheckboxes).asEventStream('change'),
            locationMaskChangedStream,
            boundsChangedStream,
            setPresetBus),

        presetChangedStream = initDropdownStream(dom.presetsDropdown);

    initTransparencySlider();

    parameterChangedStream
        .debounce(500)
        .map(getBreaksUrl)
        .doAction(_.partial(onParamChanged, _map))
        .filter(R.not(_.isNull))
        .doAction(showLoadingSpinner)
        .flatMap(getClassBreaks)
        .doAction(hideLoadingSpinner)
        .mapError(showErrorMessage)
        .onValue(updatePriorityLayer, _map);

    function showLoadingSpinner() {
        _loadingControl.setLoadingText('Processing').show();
    }

    function showErrorMessage(xhr) {
        if (_.isPlainObject(xhr) && xhr.statusText === 'abort') {
            // Aborted AJAX call -- don't show a message
        } else {
            _loadingControl.setErrorText('Unable to display priorities').show();
        }
    }

    function hideLoadingSpinner() {
        _loadingControl.hide();
    }

    function changeBounds(bounds) {
        _bounds = bounds;
        removePriorityLayer(_map);
        locationMasks.setBBox(bounds.toBBoxString());
        boundsChangedStream.push(bounds);
    };

    function setPreset(preset) {
        var params = _.merge({}, getParamsFromUi(), presets.format(preset));
        initUiFromParams(params);
        setPresetBus.push();
    }


    // Update legend when dropdown values change.
    parameterChangedStream
        .map(getActiveLayers)
        .onValue(_legendControl, 'setActiveLayers');

    // Toggle layer controls when variables are checked.
    $(dom.toggleVariable).on('click', function() {
        var $el = $(this);
        if ($el.is(':checked')) {
            $el.siblings('.layer-controls').slideDown();
        } else {
            $el.siblings('.layer-controls').slideUp();
        }
    });

    // Handle selecting presets.
    presetChangedStream
        .doAction(setVariablesUi, _initialParams)
        .map(function(value) {
            var params = getParamsFromUi(),
                preset = presets.get(value);
            return _.merge({}, params, preset);
        })
        .onValue(initUiFromParams);

    if (options.preset) {
        setPreset(options.preset);
    }

    options.boundsStream.onValue(changeBounds);

    initialized = true;

    return {
        presetChangedStream: parameterChangedStream.map(getParamsFromUi).map(toPreset),
        setPreset: setPreset
    };
}

var getClassBreaks = (function() {
    var memo = {},
        pendingXhr = null;
    return function (url) {
        if (pendingXhr) {
            // Previous (outdated) call hasn't completed, so abort it
            pendingXhr.abort();
        }
        if (memo[url]) {
            return Bacon.once(memo[url]);
        }
        pendingXhr = $.ajax({
            url: url,
            contentType: 'application/json'
        });
    return Bacon.fromPromise(pendingXhr)
        .map('.classBreaks')
        .doAction(function(classBreaks) {
            memo[url] = classBreaks;
            pendingXhr = null;
        });
    };
}());

function initTransparencySlider() {
    initSliderStream(dom.transparencySlider)
        .onValue(setOpacity);
}

function initPriorityThresholdChangedStream() {
    return initSliderStream(dom.priorityThresholdSlider)
        .debounce(500);
}

function updateSliderLabel($slider, value) {
    $slider
        .closest('.row')
        .find('.layer-value')
        .text(value);
}

function initSliderStream(selector) {
    var sliderChangedStream =
        $(selector).asEventStream('slide');
    return sliderChangedStream;
}

function initDropdownStream(selector) {
    // Return custom event value instead of jQuery event.
    function processEvent(e, value) {
        return value;
    }
    return $(selector).asEventStream('dropdown-value-changed', processEvent);
}

function onParamChanged(map, breaksUrl) {
    if (_priorityLayer && breaksUrl === null) {
        // No variables are on, so remove priority layer
        removePriorityLayer(map);
    }
    updatePresetsDropdown();
}

function removePriorityLayer(map) {
    if (_priorityLayer) {
        map.removeLayer(_priorityLayer);
    }
    _priorityLayer = null;
}


function updatePresetsDropdown() {
    var params = getParamsFromUi(),
        presetId = presets.match(params),
        label = '&nbsp;';
    if (presetId) {
        label = $(dom.presetsDropdown).find('[data-value=' + presetId + ']').html();
    }
    $(dom.presetsDropdownLabel).html(label);
}

function getBreaksUrl() {
    var url = _urls.breaksUrl,
        params = { numBreaks: _numBreaks };
    return addParamsToUrl(url, params);
}

function getActiveLayers() {
    return _.filter(getAllLayers(), 'active');
}

function getAllLayers() {
    var weight = getVariableWeightDict(true),
        polarity = getVariablePolarityDict(true);

    return $(dom.toggleVariable).map(function() {
        var $checkbox = $(this),
            source = $checkbox.data('source');

        return {
            source: source,
            active: $checkbox.prop('checked'),
            title: $checkbox.data('title'),
            less: $checkbox.data('less'),
            more: $checkbox.data('more'),
            weight: weight[source] * polarity[source]
        };
    }).toArray();
}

function addParamsToUrl(url, params) {
    var activeLayers = getActiveLayers(),
        activeVariableSourceNames = _.pluck(activeLayers, 'source'),
        activeVariableWeights = _.pluck(activeLayers, 'weight');

    if (activeLayers.length === 0) {
        return null;
    } else {
        var commonParams = {
                bbox: _bounds.toBBoxString(),
                srid: 4326,
                layers: activeVariableSourceNames.join(','),
                weights: activeVariableWeights.join(','),
                layerMask: getRasterMaskValues(),
                zipCodes: locationMasks.getChosenIds().join(',')
            },
            allParams = _.extend(commonParams, params);
        url += '?' + $.param(allParams);
        return url;
    }
}

function updatePriorityLayer(map, breaks) {
    if (!breaks) {
        return;
    }
    updatePriorityThresholdSlider(breaks);
    var url = getPriorityLayerUrl(breaks);
    if (url) {
        if (!_priorityLayer) {
            var options = {
                bounds: _bounds,
                tileSize: 512,
                zoomOffset: -1  // https://github.com/locationtech/geotrellis/issues/1550
            };
            _priorityLayer = new L.TileLayer(url, options);
            _priorityLayer.on('loading', function() {
                _loadingControl.setLoadingText('Loading tiles').show();
            });
            _priorityLayer.on('load', function() {
                _loadingControl.hide();
            });
            map.addLayer(_priorityLayer);
            setOpacity();
        } else {
            _priorityLayer.setUrl(url);
        }
    }
}

function getPriorityLayerUrl(breaks) {
    var url = _urls.tileUrl,
        threshold = getPriorityThreshold(breaks),
        params = {
            colorRamp: 'blue-to-red',
            breaks: breaks.join(',')
        };
    if (threshold) {
        params.threshold = threshold;
    }
    return addParamsToUrl(url, params);
}

function setOpacity() {
    if (_priorityLayer) {
        var sliderValue = getTransparencySliderValue(),
            opacity = 1.0 - sliderValue / 100.0;
        _priorityLayer.setOpacity(opacity);
    }
}

// Update the notches on the priority threshold slider to match the number of class breaks.
function updatePriorityThresholdSlider(breaks) {
    var slider = $(dom.priorityThresholdSlider).data('slider');
    // The slider should always have at least 2 values.
    var sliderMax = Math.max(2, breaks.length);
    // If the number of notches changed, redraw the slider, and reset the selected value.
    if (sliderMax != slider.max) {
        slider.max = sliderMax;
        slider.setValue(sliderMax);
    }
}

function getPriorityThreshold(breaks) {
    // Discussion using nBreaks = 10
    // Breaks give pixel values corresponding to histogram percentiles.
    // Percentiles are [10%, 20%, ..., 100%]
    // Slider indexes are [1, 2, ..., 10] (left to right in UI)
    // Index 1 ("best values") -- set threshold at 90% percentile, breaks[8]
    // Index 9                 -- set threshold at 10% percentile, breaks[0]
    // Index 10 ("all values") -- no threshold
    var sliderValue = getPriorityThresholdSliderValue(),
        nBreaks = breaks.length,
        i = nBreaks - sliderValue - 1;
    if (i < 0) {
        // all values -- no threshold
        return null;
    } else {
        return breaks[i];
    }
}

function getPriorityThresholdSliderValue() {
    return $(dom.priorityThresholdSlider).data('slider').getValue();
}

function getTransparencySliderValue() {
    return $(dom.transparencySlider).data('slider').getValue();
}

function getActiveVariableSourceNames() {
    return getActiveVariableDataAttribute('source');
}

function getActiveVariableDataAttribute(dataAttr) {
    return $(dom.toggleVariable + ':checked').map(function() {
        return $(this).data(dataAttr);
    }).toArray();
}

function getParamsFromUi() {
    if (!initialized) {
        $(dom.sliders).slider({value: 0, tooltip: 'hide'});
    }

    var params = {
        priorityThreshold: getPriorityThresholdSliderValue(),
        transparency: getTransparencySliderValue(),
        activeVariables: getActiveVariableSourceNames(),
        weights: {
            categories: getCategoryWeightDict(), // {biodiversity: 3, socioeconomic: 6}
            variables: getLayerWeights(),  // {nlcd: 3, 30yr_temp: 6}
            expanded: getExpandedLayerCategories() // ['biodiversity']
        },
        masks: {
            variables: getCheckedRasterMaskNames(), // {nlcd: ['industrial', 'commercial']}
            boundaryIds: locationMasks.getChosenIds()  // ['19123', '19124']
        }
    };
    return params;

    function getCategoryWeightDict() {
        return _.zipObject(
            getDataAttributeValues(dom.categorySliders, 'category'),
            getSliderValues(dom.categorySliders)
        );
    }

    function getExpandedLayerCategories() {
        return getDataAttributeValues(dom.expandedLayerSections, 'category');
    }

    function getLayerWeights() {
        return _.zipObject(_.map(getAllLayers(), function(layer) {
            return [layer.source, layer.weight];
        }));
    }
}

function toPreset(params) {
    var preset = {};
    _.each(params.activeVariables, function(variable) {
        preset[variable] = params.weights.variables[variable];
    });
    return preset;
}

function initUiFromParams(params) {
    setVariablesUi(params);
    setSliderValue($(dom.priorityThresholdSlider), params.priorityThreshold);
    setSliderValue($(dom.transparencySlider), params.transparency);

    function setSliderValue($slider, value) {
        if (!_.isUndefined(value)) {
            $slider.data('slider').setValue(value);
            updateSliderLabel($slider, value);
        }
    }

    // If we choose to re-enable collapsible sections, uncomment this line
    // and undo other changes in https://github.com/OpenTreeMap/otm-addons/pull/1172
    //showOrHideLayerSections();

    var $rasterCheckboxes = $(dom.rasterMaskCheckboxes).prop('checked', true);
    _.each(params.masks.variables, function (names, source) {
        var $checkboxesForSource = filterByAttribute($rasterCheckboxes, 'data-source', source);
        // Boxes are checked by default. Uncheck all, then check specific ones.
        $checkboxesForSource.prop('checked', false);
        checkBoxes($checkboxesForSource, 'data-name', names);
    });

    locationMasks.setChosenIds(params.masks.boundaryIds);

    function checkBoxes($checkboxes, attName, values) {
        _.each(values, function (value) {
            var $checkbox = filterByAttribute($checkboxes, attName, value);
            $checkbox.prop('checked', true);
        });
    }

    function showOrHideLayerSections() {
        $(dom.layerSections).each(function () {
            var $section = $(this),
                show = _.contains(params.weights.expanded, $section.data('category'));
            // This "if" is required because "hide" fails when there's no "in" class
            // https://github.com/twbs/bootstrap/issues/7042
            if (show || $section.hasClass('in')) {
                $section.collapse(show ? 'show' : 'hide');
            }
        });
    }
}

function setVariablesUi(params) {
    var polarityValues = _.mapValues(params.weights.variables, Math.sign),
        weightValues = _.mapValues(params.weights.variables, Math.abs);
    setActiveVariables(params.activeVariables);
    setDropdownValues(dom.weightDropdowns, weightValues);
    setDropdownValues(dom.polarityDropdowns, polarityValues);

    function setActiveVariables(sources) {
        // Mark layer checkboxes.
        $(dom.toggleVariable).removeProp('checked');
        _.each(sources, function(source) {
            var $chk = $(dom.toggleVariable + '[data-source="' + source + '"]');
            $chk.prop('checked', 'checked');
        });

        // Show dropdown controls for active layers only.
        $(dom.toggleVariable).siblings('.layer-controls').hide();
        $(dom.toggleVariable + ':checked').siblings('.layer-controls').show();
    }

    function setDropdownValues(selector, variables) {
        // For backwards compatibility with old saved plans.
        if (!variables) {
            return;
        }
        $(selector).each(function() {
            var $dropdown = $(this),
                source = $dropdown.data('source'),
                value = variables[source];

            // Try to set the dropdown value. If the value doesn't exist
            // in the dropdown, it may be a custom value. So try that next.
            try {
                dropdowns.setValue($dropdown, value);
            } catch(ex) {
                dropdowns.setCustomValue($dropdown, value);
            }
        });
    }
}

// Returns e.g. {nlcd: 3, 30yr_temp: 6}
function getVariableWeightDict() {
    return getDropdownValues(dom.weightDropdowns);
}

// Returns e.g. {nlcd: 1, 30yr_temp: -1}
function getVariablePolarityDict() {
    return getDropdownValues(dom.polarityDropdowns);
}

function getDropdownValues(selector) {
    var result = {};
    $(selector).each(function() {
        var $dropdown = $(this),
            source = $dropdown.data('source');
        result[source] = dropdowns.getValue($dropdown);
    });

    return result;
}

function getRasterMaskValues() {
    var masks = {},
        $all = $(dom.rasterMaskCheckboxes),
        $checked = $(dom.rasterMaskCheckboxes + ':checked');
    if ($checked.length === $all.length) {
        return '';
    } else {
        $checked.each(function () {
            var source = $(this).data('source'),
                values = $(this).data('values').toString().split(',');
            if (!masks[source]) {
                masks[source] = [];
            }
            masks[source] = masks[source].concat(_.map(values, Number));
        });
        return JSON.stringify(masks);
    }
}

function getCheckedRasterMaskNames() {
    // Returns list of checked mask options for each data source,
    // e.g. {landCover: ['industrial', 'commercial"], ... }
    // Omits entry for a data source if all its boxes are checked
    var $rasterCheckboxes = $(dom.rasterMaskCheckboxes),
        sources = _.uniq(getDataAttributeValues($rasterCheckboxes, 'source')),
        result = {};
    _.each(sources, function (source) {
        var $checkboxes = filterByAttribute($rasterCheckboxes, 'data-source', source),
            $checked = $checkboxes.filter(':checked');
        if ($checked.length < $checkboxes.length) {
            result[source] = getDataAttributeValues($checked, 'name');
        }
    });
    return result;
}

// Get values for given data attribute across given selector
function getDataAttributeValues(selector, attrName) {
    var values = mapDataAttributeValues(selector, attrName, _.identity);
    return values;
}

// Return a list of slider values, for all elements matching the selector.
function getSliderValues(selector) {
    var values = mapDataAttributeValues(selector, 'slider', function(slider) {
        return slider && slider.getValue();
    });
    return values;
}

// Map the same data attribute, for all elements matching the selector, with the given function.
function mapDataAttributeValues(selector, attrName, fn) {
    var $items = _.isString(selector) ? $(selector) : selector;
    return $items
        .map(function() {
            return fn($(this).data(attrName));
        })
        .get();
}

function filterByAttribute($items, attName, attValue) {
    var qualifier = '[' + attName + '="' + attValue + '"]';
    return $items.filter(qualifier);
}

module.exports = {
    init: init
};
