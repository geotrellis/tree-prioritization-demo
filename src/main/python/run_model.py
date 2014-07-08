import math
import random
from operator import itemgetter

from Tree import Tree


def run_model(scenario):
    """ Input dictionary should look like:
    { 'groups': [{
                 "species": species_id,
                 "diameter": diameter in cm,
                 "count"}, ...],
      'trees': [{
                 "species": species_id,
                 "diameter": diameter in cm,
                 "location": [x, y],
                 "land_use_category": category}, ...],
      'land_use_histogram': [(category, count), ...],
      'mortality': mortality percentage (trees to kill each year),
      'years': number of years }
    """
    mortality = float(scenario.get("mortality", "0.05"))
    trees = scenario["trees"]
    groups = scenario["groups"]
    land_use_hist = scenario["land_use_histogram"]

    # Build list of live trees
    live_trees = []
    for group in groups:
        live_trees += build_tree_grouping(group, land_use_hist)

    for tree in trees:
        live_trees.append(Tree(
            int(tree["species"]),
            float(tree["diameter"]),
            tree["land_use_category"]))

    # Growth kill cycle
    years = int(scenario["years"])
    output = {'years': []}

    all_dead = []
    next_years_carryover = 0.0
    for i in range(0, years):
        # Select and kill trees
        live_trees, dead_trees, next_years_carryover = kill_trees(live_trees, mortality, next_years_carryover)
        all_dead += dead_trees
        # Add growth to living trees
        for tree in live_trees:
            tree.grow()

        output['years'].append({
            'year': i,
            'live':[tree.get_json() for tree in live_trees],
            'killed':[tree.get_json() for tree in dead_trees]
        })

    return output


def build_tree_grouping(group, land_use_hist):
    diameter_cm = int(group["diameter"])
    species_id = group["species"]
    n_trees = int(group["count"])
    tree_counts = get_tree_counts(land_use_hist, n_trees)
    trees = []

    for (land_use_category, n) in tree_counts:
        for i in range(0, n):
            trees.append(Tree(species_id, diameter_cm, land_use_category))

    return trees


def get_tree_counts(land_use_hist, n_trees):
    """
    Distribute n_trees among land use categories,
    with a distribution matching land_use_hist
    """
    area_total = sum([area for (category, area) in land_use_hist])
    scale = n_trees / float(area_total)

    tree_counts = [(category, math.round(scale * area ))
                    for (category, area) in land_use_hist]

    # If we ended up with the wrong number of trees (due to roundoff error),
    # Add or remove a tree from a random category until we have the
    # right number of trees.

    tree_total = sum([count for (category, count) in tree_counts])
    roundoff_error = n_trees - tree_total
    roundoff_sign = roundoff_error / math.abs(roundoff_error)
    n_categories = len(tree_counts)

    while roundoff_error:
        category = random.random() * n_categories
        if tree_counts[category][1] + roundoff_sign >= 0:
            tree_counts[category][1] += roundoff_sign
            roundoff_error -= roundoff_sign

    return tree_counts


def kill_trees(live_trees, kill_percent, fractional_carryover_kill):
    kill_count = len(live_trees) * kill_percent
    kill_count += fractional_carryover_kill
    
    next_years_carryover = kill_count - int(kill_count)
    kill_count = int(kill_count)

    # Decide which trees to kill
    dead_trees = []
    for i in range(0, kill_count):
        kill_this_tree = weighted_choice(live_trees)
        dead_tree = live_trees.pop(kill_this_tree)
        dead_tree.is_dead = True
        dead_trees.append(dead_tree)

    return live_trees, dead_trees, next_years_carryover


def weighted_choice(trees):
    totals = []
    running_total = 0

    for t in trees:
        running_total += t.get_kill_weight()
        totals.append(running_total)

    rnd = random.random() * running_total
    for i, total in enumerate(totals):
        if rnd <= total:
            return i


