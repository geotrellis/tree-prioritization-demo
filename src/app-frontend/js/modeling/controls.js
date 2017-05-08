"use strict";

var $ = require('jquery'),
    L = require('leaflet'),
    template = require('./template.js');

var templates = {
    legendControl: '#legend-control-tmpl',
    loadingControl: '#loading-control-tmpl'
};

var BaseControl = L.Control.extend({
    onAdd: function() {
        var el = this.$el.get(0);
        L.DomEvent.disableClickPropagation(el);
        return el;
    },

    hide: function() {
        this.$el.hide();
        return this;
    },

    show: function() {
        this.$el.show();
        return this;
    }
});

var LegendControl = BaseControl.extend({
    options: {
        position: 'bottomleft'
    },

    initialize: function() {
        this.$el = $('<div>');
        this.render();
    },

    setActiveLayers: function(layers) {
        this.render(layers);
    },

    render: function(layers) {
        var html = template.render(templates.legendControl, {
            layers: layers || []
        });
        this.$el.html(html);
        return this;
    }
});

var LoadingControl = BaseControl.extend({
    options: {
        position: 'topleft'
    },

    initialize: function() {
        this.$el = $(template.render(templates.loadingControl));
    },

    setText: function(text) {
        this.$el.find('.modeling-error-icon').hide();
        this.$el.find('.modeling-loading-icon').hide();
        this.$el.find('.text').text(text);
        return this;
    },

    setErrorText: function(text) {
        this.setText(text);
        this.$el.find('.modeling-error-icon').show();
        return this;
    },

    setLoadingText: function(text) {
        this.setText(text);
        this.$el.find('.modeling-loading-icon').show();
        return this;
    }
});


module.exports = {
    LegendControl: LegendControl,
    LoadingControl: LoadingControl
};

