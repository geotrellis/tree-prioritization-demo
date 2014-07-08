
DBH_BREAKS = [0, 8, 16, 31, 47, 62, 77]
DBH_MORTALITY = [9.0, 6.4, 4.3, 0.5, 3.3, 1.8, 3.1]


GROWTH_RATES = dict([
    ('Fraxinus', [.90,  .99,  .85, .64,  .68,  .70,  .44]),
    ('Ulmus',    [.96, 1.15, 1.08, .89,  .83,  .83, 1.03]),
    ('Acer',     [.81,  .92,  .79, .68,  .66,  .72, 1.11]),
    ('Populus',  [.64, 1.06,  .98, .94, 1.49, 1.61, 1.87]),
    ('Other',    [.81, 1.10,  .87, .73,  .73,  .71,  .42])
])

class Tree(object): 
    
    nId = 0

    @classmethod
    def next_id(clazz):
        if Tree.nId >= 2147483646:
            Tree.nId = 0
            
        Tree.nId += 1

        return Tree.nId - 1

    def __init__(self, diameter, baseKillPct, speciesId, zoneId):
        self.treeId = Tree.next_id()
        self.zoneId = zoneId
        self.baseKillPct = baseKillPct
        self.is_dead = False
        self.species = Species.objects.get(pk=speciesId)
        #todo: assumes cm
        self.diameter = diameter

    def get_json(self):
        return {'zone_id': self.zoneId, 
                'is_dead': self.is_dead, 
                'species': self.species.common_name, 
                'diameter': self.diameter, 
                'eco': self.eco,
                'tid': self.treeId }

    def get_weight(self):
        index = 0
        interp = 0

        dbh_weight = self.get_interp(self.diameter, DBH_BREAKS, DBH_MORTALITY)
        species_weight = self.species.kill_weight      
        return dbh_weight + self.baseKillPct + species_weight

    def get_interp(self, unit, lookup, values):
        if unit >= lookup[-1]:
            index = len(lookup)-1
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
        #Match species against growth table
        dbh_rates = GROWTH_RATES.get(self.species.genus, GROWTH_RATES['Other'])
        # Figure out how much to grow
        dbh_increase = self.get_interp(self.diameter, DBH_BREAKS, dbh_rates)

        self.diameter += dbh_increase
        return

