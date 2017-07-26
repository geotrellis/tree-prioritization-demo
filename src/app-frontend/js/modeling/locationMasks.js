"use strict";

var $ = require('jquery'),
    _ = require('lodash'),
    Bacon = require('baconjs'),
    BU = require('./baconUtils.js'),
    otmTypeahead = require('./otmTypeahead.js'),
    template = require('./template.js');

var dom = {
    chosenMasksList: '#chosen-masks',
    chosenBoundaryTemplate: '#boundary-mask-tmpl',
    boundaryInput: '#boundary-mask-typeahead',
    btnRemove: '.remove-mask',
    boundaryError: '#boundary-error'
};

var boundaries = {},
    chosenIds = [],
    bbox = '0,0,0,0';

function init(options) {
    var addBoundaryStream = BU.searchBoxStream(dom.boundaryInput),
        removeBoundaryStream = $(dom.chosenMasksList).asEventStream('click', dom.btnRemove),
        changedBus = new Bacon.Bus(),
        zipCodeUrlPrefix = options.urls.zipCodeUrl;

    bbox = options.instanceBounds.toBBoxString();

    function zipCodeUrl(zipCode) {
        return zipCodeUrlPrefix + '/' + zipCode + '?bbox=' + bbox;
    }

    var boundaryRequestStream = addBoundaryStream.map(zipCodeUrl).flatMap(BU.getJsonFromUrl);

    // clear any previous errors when the user starts typing a value
    $(dom.boundaryInput).on('keyup', function() { $(dom.boundaryError).html(''); });

    boundaryRequestStream.onValue(function (boundary) {
        chosenIds.push(boundary.id);
        setChosenIds(chosenIds);
        $(dom.boundaryInput).val('').focus();
        changedBus.push(boundary.id);
    });

    boundaryRequestStream.onError(function (e) {
        $(dom.boundaryError).html(e.responseText);
        $(dom.boundaryInput).focus().select();
    });

    removeBoundaryStream.onValue(function (e) {
        var id = $(e.currentTarget).data('boundary-id');
        setChosenIds(_.without(chosenIds, id.toString()));
        changedBus.push(id);
    });

    return changedBus.toEventStream();
}

function setChosenIds(ids) {
    chosenIds = ids;
    var $container = $(dom.chosenMasksList);
    $container.empty();
    _.each(ids, function (id) {
        var html = template.render(dom.chosenBoundaryTemplate, {
            id: id
        });
        $container.append(html);
    });
}

function getChosenIds() {
    return chosenIds;
}

function setBBox(newBBox) {
    bbox = newBBox;
    setChosenIds([]);
}

module.exports = {
    init: init,
    setChosenIds: setChosenIds,
    getChosenIds: getChosenIds,
    setBBox: setBBox
};
