"use strict";

var $ = require('jquery');

var dom = {
    dropdown: '#layers .dropdown',
    label: '[data-toggle="dropdown"] .dropdown-label',
    items: '.dropdown-menu li a',
    customWeight: '[data-custom]'
};

// Bind event listener to each dropdown item so we can trigger a custom
// event (for Bacon streams) and update the dropdown label.
function init() {
    $(dom.dropdown).on('click', dom.items, function() {
        var $item = $(this),
            $dropdown = $item.parents('.dropdown'),
            newValue = $item.data('value');

        if (newValue === 'custom') {
            setCustomValue($dropdown, getValue($dropdown));
        } else {
            setValue($dropdown, newValue);
        }
    });

    $(dom.dropdown).on('change', dom.customWeight, function() {
        var $el = $(this),
            $dropdown = $el.parents('[data-source]'),
            newValue = $el.val();

        setCustomValue($dropdown, newValue);
    });
}

function getValue($dropdown) {
    var value = $dropdown.data('value');
    if (value === 'custom') {
        return $dropdown.find(dom.customWeight).val();
    } else {
        return value;
    }
}

function setValue($dropdown, value) {
    var $label = $dropdown.find(dom.label),
        $item = $dropdown.find('[data-value="' + value + '"]'),
        oldValue = getValue($dropdown);

    if (value === 'custom') {
        enableCustomField($dropdown);
    } else {
        disableCustomField($dropdown);
    }

    if ($item.size() === 0) {
        throw new Error('Value does not exist in dropdown: ' + value);
    }

    $label.html($item.html());
    $dropdown.data('value', value);

    if (value != oldValue && value !== 'custom') {
        $dropdown.trigger('dropdown-value-changed', value);
    }
}

function setCustomValue($dropdown, value) {
    var $customWeight = $dropdown.find(dom.customWeight);

    setValue($dropdown, 'custom');
    $customWeight.val(value);

    $dropdown.trigger('dropdown-value-changed', value);
}

function getItems($dropdown) {
    return $dropdown.find(dom.items);
}

function enableCustomField($dropdown) {
    $dropdown.find(dom.customWeight).removeClass('hide').focus();
    $dropdown.find('.input-group-disabled').attr('class', 'input-group');
    $dropdown.find('.input-group-btn-disabled').attr('class', 'input-group-btn');
}

function disableCustomField($dropdown) {
    $dropdown.find(dom.customWeight).addClass('hide');
    $dropdown.find('.input-group').attr('class', 'input-group-disabled');
    $dropdown.find('.input-group-btn').attr('class', 'input-group-btn-disabled');
}

module.exports = {
    init: init,
    getValue: getValue,
    setValue: setValue,
    setCustomValue: setCustomValue
};
