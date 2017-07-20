var Bacon = require('baconjs');

function createGeocodeStream(addressStream) {
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
    return geocodeBus.toEventStream();
}


module.exports = {
    createGeocodeStream: createGeocodeStream
};
