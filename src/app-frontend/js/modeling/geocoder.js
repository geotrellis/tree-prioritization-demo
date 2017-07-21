var $ = require('jquery'),
    Bacon = require('baconjs'),
    BU = require('./baconUtils.js');

function searchBoxStream(inputSelector) {
    return  $(inputSelector)
        .asEventStream('keyup')
        .map(function () { return $(inputSelector).val();})
        .sampledBy(BU.enterOrClickEventStream({inputs: inputSelector}));
}

function createAutocomplete(textbox) {
    var placeTextBus = new Bacon.Bus();
    var placePointBus = new Bacon.Bus();
    // Limit suggestions to the continental US because that is the area covered by the rasters we have.
    var usBounds = {north: 49.38237, east: -66.18164, south: 24.68695, west: -125.24414};
    var autocomplete = new google.maps.places.Autocomplete($(textbox)[0], {strictBounds: true, bounds: usBounds});
    autocomplete.addListener('place_changed', function handlePlaceChanged() {
        var place = autocomplete.getPlace();
        if (place.geometry) {
            placePointBus.push(place.geometry.location.toJSON());
        }
    });
    return placePointBus.toEventStream();
}

function createGeocodeStream(textbox) {
    var placePointStream = createAutocomplete(textbox);
    var addressStream = searchBoxStream(textbox);
    var geocoder = new google.maps.Geocoder(),
        geocodeBus = new Bacon.Bus(),
        prepareAddress = function (address) { return {'address': address}; },
        geocode = function (searchValue) {
            geocoder.geocode(searchValue, function(results, status) {
                if (status === 'OK') {
                    geocodeBus.push(results[0].geometry.location.toJSON());
                } else {
                    geocodeBus.error('Failed to find location. ' + status);
                }
            });
        };
    addressStream.map(prepareAddress).onValue(geocode);
    return geocodeBus.merge(placePointStream);
}

module.exports = {
    createGeocodeStream: createGeocodeStream
};
