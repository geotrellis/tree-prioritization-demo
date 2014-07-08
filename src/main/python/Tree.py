
_DBH_BREAKS = [0, 8, 16, 31, 47, 62, 77]
_DBH_MORTALITY = [9.0, 6.4, 4.3, 0.5, 3.3, 1.8, 3.1]

_GROWTH_RATES = dict([
    ('Fraxinus', [.90,  .99,  .85, .64,  .68,  .70,  .44]),
    ('Ulmus',    [.96, 1.15, 1.08, .89,  .83,  .83, 1.03]),
    ('Acer',     [.81,  .92,  .79, .68,  .66,  .72, 1.11]),
    ('Populus',  [.64, 1.06,  .98, .94, 1.49, 1.61, 1.87]),
    ('Other',    [.81, 1.10,  .87, .73,  .73,  .71,  .42])
])


class Tree(object):
    
    nId = 0

    @classmethod
    def _next_id(clazz):
        if Tree.nId >= 2147483646:
            Tree.nId = 0
            
        Tree.nId += 1

        return Tree.nId - 1

    def __init__(self, species_id, diameter, land_use_weight):
        self.treeId = Tree._next_id()
        self.land_use_weight = land_use_weight
        self.is_dead = False
        self.species = Species.objects.get(pk=species_id)
        #todo: assumes cm
        self.diameter = diameter

    def get_kill_weight(self):
        """
        Return tree's relative likelihood of dying in a given cycle
        by combining factors from its diameter, species, and land use type
        """
        dbh_weight = self._get_interp(self.diameter, _DBH_BREAKS, _DBH_MORTALITY)
        species_weight = self.species.kill_weight      
        return dbh_weight + species_weight + self.land_use_weight

    def _get_interp(self, unit, lookup, values):
        if unit >= lookup[-1]:
            index = len(lookup) - 1
            interp = 1
        for i, d in enumerate(lookup):
            if unit < d:
                #todo: stop doing this for every value < d
                index = i
                interp = (float(unit - lookup[i-1]) / float(lookup[i] - lookup[i-1]))
                break
        value = (float(values[index] - values[index-1]) * interp) + values[index-1]

        return value

    def grow(self):
        # Match species against growth table
        dbh_rates = _GROWTH_RATES.get(self.species.genus, _GROWTH_RATES['Other'])
        # Figure out how much to grow
        dbh_increase = self._get_interp(self.diameter, _DBH_BREAKS, dbh_rates)

        self.diameter += dbh_increase
        return
