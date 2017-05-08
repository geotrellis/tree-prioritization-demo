"use strict";

var $ = require('jquery'),
    _ = require('lodash'),
    Bacon = require('baconjs'),
    otmTypeahead = require('./otmTypeahead.js'),
    template = require('./template.js');

var dom = {
    chosenMasksList: '#chosen-masks',
    chosenBoundaryTemplate: '#boundary-mask-tmpl',
    typeaheadInput: '#boundary-mask-typeahead',
    btnRemove: '.remove-mask'
};

var boundaries = {},
    chosenIds = [];

function init() {
    var typeahead = otmTypeahead.create({
        name: "boundaryMasks",
        url: "",  // TODO: fetch names of zip codes intersecting map bounds
        input: dom.typeaheadInput,
        template: "#boundary-element-template",
        hidden: "#boundary-mask",
        reverse: "id",
        sortKeys: ['sortOrder', 'value']
    });

    typeahead.allDataStream.onValue(function (data) {
        _.each(data, function (boundary) {
            boundaries[boundary.id] = boundary;
        });
    });

    var addBoundaryStream = typeahead.selectStream,
        removeBoundaryStream = $(dom.chosenMasksList).asEventStream('click', dom.btnRemove),
        changedStream = Bacon.mergeAll(addBoundaryStream, removeBoundaryStream);

    addBoundaryStream.onValue(function (boundary) {
        chosenIds.push(boundary.id);
        setChosenIds(chosenIds);
        typeahead.clear();
    });

    removeBoundaryStream.onValue(function (e) {
        var id = $(e.currentTarget).data('boundary-id');
        setChosenIds(_.without(chosenIds, id));
    });

    return changedStream;
}

function setChosenIds(ids) {
    chosenIds = ids;
    var $container = $(dom.chosenMasksList);
    $container.empty();
    _.each(ids, function (id) {
        var html = template.render(dom.chosenBoundaryTemplate, {
            boundary: boundaries[id]
        });
        $container.append(html);
    });
}

function getChosenIds() {
    return chosenIds;
}

module.exports = {
    init: init,
    setChosenIds: setChosenIds,
    getChosenIds: getChosenIds
};
