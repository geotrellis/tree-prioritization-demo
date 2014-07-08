from copy import copy
from random import random


class GrowthModel(object):

    # ------------------------------------------------------------------------
    # Public methods

    def __init__(self, params):
        """
        Initialize model with growth and mortality parameters.
        See baltimore_chicago_model.py for an example.
        """
        data = params['growth']['byGenusAndDiameter']
        # Minimum diameters for categories (cm)
        self._growth_breaks = data['breaks']
        # Map genus name to growth rates by diameter category
        self._growth = {row['genus']: row['cmPerYear']
                        for row in data['rates']}
        # Default growth rates by diameter category
        self._default_growth = data['defaultCmPerYear']

        data = params['mortality']['byLandUse']
        # Map category codes to category names
        self._land_use_names = [row['category'] for row in data]
        # Map category codes to mortality
        self._mortality_by_land_use = [row['mortality'] for row in data]

        data = params['mortality']['byDiameter']
        # Minimum diameters for categories (cm)
        self._mortality_by_diameter_breaks = data['breaks']
        # Map diameter category to mortality
        self._mortality_by_diameter = data['mortality']

        # Map species id to mortality
        # TODO: relate species name to OTM species ID
        _mortality_by_species = {}

    def get_land_use_categories(self, scenario):
        return self._land_use_names

    def run(self, scenario):
        """
        Input scenario dictionary:
        { 'groups': [{
                     'species': species_id,
                     'diameter': diameter (cm),
                     'count'}, ...],
          'trees': [{
                     'species': species_id,
                     'diameter': diameter (cm),
                     'land_use_category': category}, ...],
          'land_use_histogram': [(category, count), ...],
          'mortality': percent of trees to kill each year,
          'years': number of years }

        Output: for each year, data summarizing living trees:
            [ [{'species': species_id, 'diameter: diameter}, ... ],
              ...]
        """
        mortality = float(scenario['mortality'])
        tree_specs = scenario['trees']
        group_specs = scenario['groups']
        land_use_hist = scenario['land_use_histogram']
        n_years = int(scenario['years'])

        # Build list of live trees
        live_trees = [GrowthModel.Tree(spec) for spec in tree_specs]
        for group in group_specs:
            live_trees += self._make_trees_for_group(group, land_use_hist)

        return self._growth_kill_cycle(n_years, mortality, live_trees)

    # ------------------------------------------------------------------------
    # Create trees

    class Tree(object):
        def __init__(self, spec, outer):
            self.diameter = int(spec['diameter'])
            self.species_id = spec['species']
            self.species_weight = outer._mortality_by_species(self.species_id,
                                                              0)
            genus = ''  # TODO
            self.growth_by_diameter = outer._growth.get(genus,
                                                        outer._default_growth)
            if 'land_use_category' in spec:
                category = spec['land_use_category']
                self.land_use_weight = outer._mortality_by_land_use[category]

        def summary(self):
            return {
                'species': self.species_id,
                'diameter': self.diameter
            }

    def _make_trees_for_group(self, spec, land_use_hist):
        n_trees = int(spec['count'])
        tree_counts = self._get_tree_counts(land_use_hist, n_trees)
        trees = []
        tree = GrowthModel.Tree(spec)

        for (category, n) in tree_counts:
            land_use_weight = self._mortality_by_land_use[category]
            for i in range(0, n):
                t = copy(tree)
                t.land_use_weight = land_use_weight
                trees.append(t)

        return trees

    def _get_tree_counts(self, land_use_hist, n_trees):
        """
        Distribute n_trees among land use categories,
        with a distribution matching land_use_hist
        """
        area_total = sum([area for (category, area) in land_use_hist])
        scale = n_trees / float(area_total)

        tree_counts = [(category, round(scale * area ))
                       for (category, area) in land_use_hist]

        # If we ended up with the wrong number of trees (due to roundoff error)
        # add or remove a tree from a random category until we have the
        # right number of trees.

        tree_total = sum([count for (category, count) in tree_counts])
        roundoff_error = n_trees - tree_total
        roundoff_sign = roundoff_error / abs(roundoff_error)
        n_categories = len(tree_counts)

        while roundoff_error:
            category = random() * n_categories
            if tree_counts[category][1] + roundoff_sign >= 0:
                tree_counts[category][1] += roundoff_sign
                roundoff_error -= roundoff_sign

        return tree_counts

    # ------------------------------------------------------------------------
    # Run model

    def _growth_kill_cycle(self, n_years, mortality, live_trees):
        output = []
        next_years_carryover = 0.0
        for i in range(0, n_years):
            # Select and kill trees
            live_trees, dead_trees, next_years_carryover = \
                self._kill_trees(live_trees, mortality, next_years_carryover)

            # Add growth to living trees
            for tree in live_trees:
                self._grow_tree(tree)

            output.append([tree.summary() for tree in live_trees])

        return output

    def _kill_trees(self, live_trees, kill_percent, fractional_carryover_kill):
        kill_count = len(live_trees) * kill_percent
        kill_count += fractional_carryover_kill

        next_years_carryover = kill_count - int(kill_count)
        kill_count = int(kill_count)

        # Decide which trees to kill
        dead_trees = []
        for i in range(0, kill_count):
            index_to_kill = self._choose_tree_to_kill(live_trees)
            dead_tree = live_trees.pop(index_to_kill)
            dead_trees.append(dead_tree)

        return live_trees, dead_trees, next_years_carryover

    def _choose_tree_to_kill(self, trees):
        """
        Choose a tree to kill. The probability that a given tree will be
        chosen is proportional to its 'kill weight'.
        """
        totals = []
        running_total = 0

        for tree in trees:
            running_total += self._get_kill_weight(tree)
            totals.append(running_total)

        rnd = random() * running_total
        for i, total in enumerate(totals):
            if rnd <= total:
                return i

    def _get_kill_weight(self, tree):
        """
        Return tree's relative likelihood of dying in a given cycle
        by combining factors from its diameter, species, and land use type
        """
        diameter_weight = self._interpolate_value(
            tree.diameter,
            self._mortality_by_diameter_breaks,
            self._mortality_by_diameter)
        return diameter_weight + tree.species_weight + tree.land_use_weight

    def _grow_tree(self, tree):
        rate = self._interpolate_value(tree.diameter,
                                       self._growth_breaks,
                                       tree.growth_by_diameter)
        tree.diameter += rate

    def _interpolate_value(self, unit, breaks, values):
        if unit >= breaks[-1]:
            return values[-1]

        for i, low_val in enumerate(breaks):
            if unit <= low_val:
                interp = float(unit - breaks[i-1]) / (breaks[i] - breaks[i-1])
                value = values[i-1] + interp * (values[i] - values[i-1])
                return value
