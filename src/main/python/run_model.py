import math
import random
import urllib2
import simplejson
from operator import itemgetter

from Tree import Tree


def run_model(scenario_json):
    """ Input json should look like:
    { geometry: [[x1,y1],[x2,y2],...],
      groups: [{
                 "species": species_id,
                 "diameter": diameter in inches,
                 "count" }, ...],
      trees: [{
                 "species": species_id,
                 "diameter": diameter in inches,
                 "location": [x, y] }, ...],
      mortality: mortality percentage (trees to kill each year),
      years: number of years }

    A histogram is build via trellis and groups of trees are auto-
    assigned based on species and kill-percentage (from histogram)

    Tree locations are sent directly to trellis and then assigned
    a kill percentage
    """
    scenario_params = simplejson.loads(scenario_json)

    mortality = float(scenario_params.get("mortality", "0.05"))
    geometry = scenario_params["geometry"]
    trees = scenario_params["trees"]
    groups = scenario_params["groups"]

    hist, pts = get_trellis_histogram(geometry, [tree["location"] for tree in trees])

    # get list of live trees
    live_trees = []
    for group in groups:
        live_trees += build_tree_grouping(group, hist)

    for (tree, pctg) in zip(trees, pts):
        species = tree["species"].split("_")[0]
        live_trees.append(Tree(int(species), float(tree["diameter"]), pctg))

    # Growth kill cycle
    years = int(scenario_params["years"])
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


def get_trellis_histogram(poly, pts):
    """ Given a list of points forming a polygon (poly) and a list of points,
    return the land use histogram as well as the land use underneath each of
    points"""

    encoded_polys = ",".join(["%s %s" % (p[0], p[1]) for p in poly]).replace(" ","%20")
    pts = ",".join(["%s %s" % (p[0], p[1]) for p in pts]).replace(" ","%20")
    url = settings.TRELLIS_URL + "scenarioHistogram?polygons=%s&points=%s" % (encoded_polys,pts)

    print "Trellis URL: %s" % url

    json = simplejson.loads(urllib2.urlopen(url).read())
    return (json["hist"][0],json["pts"])


def build_tree_grouping(group, hist):

    diameter = int(group["diameter"])
    species = group["species"].split("_")[0]

    nTrees = int(group["count"])
    hist = hist_to_treepct(hist, nTrees)
    trees = []

    for (land_use_weight, n) in hist:
        for i in range(0, n):
            trees.append(Tree(species, diameter, land_use_weight))

    return trees


def hist_to_treepct(hist, nTrees):
    """ Convert a histogram of kill values to # of trees for given kill values """
    
    area_total = sum([n for (h, n) in hist])
    hist = sorted(hist, key=itemgetter(1))
    trees = []

    for (category, area) in hist:
        if nTrees > 0:
            pct = area / float(area_total)
            trees_in_category = math.ceil(nTrees * pct)
            nTrees - trees_in_category
            area_total -= area
            trees.append([category, int(trees_in_category)])

    return trees


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


