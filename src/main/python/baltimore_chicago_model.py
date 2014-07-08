# Parameters for tree growth and mortality model.
#
# Representation:
#    o JSON-compatible for easy client/server transfer and DB storage
#    o Use lists for tables to preserve row order for client-side display and editing
#    o Use dicts for table rows to add clarity in JS code

model = {
    "growth": {
        "byGenusAndDiameter": {
            "breaks": [0, 8, 16, 31, 47, 62, 77],
            "rates": [
                {"genus": "Fraxinus", "cmPerYear": [.90,  .99,  .85, .64,  .68,  .70,  .44]},
                {"genus": "Ulmus",    "cmPerYear": [.96, 1.15, 1.08, .89,  .83,  .83, 1.03]},
                {"genus": "Acer",     "cmPerYear": [.81,  .92,  .79, .68,  .66,  .72, 1.11]},
                {"genus": "Populus",  "cmPerYear": [.64, 1.06,  .98, .94, 1.49, 1.61, 1.87]}
            ],
            "defaultCmPerYear": [.80, 1.10,  .87, .73,  .73,  .71,  .42]
        }
    },
    "mortality": {
        "byDiameter": {
            "breaks":    [0.0, 7.7, 15.3, 30.6, 45.8, 61.1, 76.3],
            "mortality": [9.0, 6.4,  4.3,  0.5,  3.3,  1.8,  3.1]
        },
        "byLandUse": [
            {"category": "Forest"                        , "mortality":  5.9},
            {"category": "Urban Open"                    , "mortality":  8.2},
            {"category": "Low-Medium Density Residential", "mortality":  2.2},
            {"category": "High Density Residential"      , "mortality":  6.0},
            {"category": "Transportation"                , "mortality": 20.2},
            {"category": "Commercial/Industrial"         , "mortality": 10.6},
            {"category": "Institutional"                 , "mortality":  0.0},
            {"category": "Barren"                        , "mortality":  0.0}
        ],
        "bySpecies": [
            {"mortality": 18.9, "species": "Morus alba L."},
            {"mortality": 17.6, "species": "Ailanthus altissima (P. Mill.) Swingle"},
            {"mortality": 13.2, "species": "Cornus florida L."},
            {"mortality": 12.6, "species": "Acer negundo L."},
            {"mortality":  9.0, "species": "Acer saccharinum L."},
            {"mortality":  7.4, "species": "Robinia pseudoacacia L."},
            {"mortality":  7.1, "species": "Ulmus parvifolia Jacq."},
            {"mortality":  6.8, "species": "Fraxinus americana L./F. pennsylvanica Marsh."},
            {"mortality":  6.5, "species": "Sassafras albidum (Nutt.) Nees"},
            {"mortality":  6.2, "species": "Quercus phellos L."},
            {"mortality":  6.1, "species": "Acer platanoides L."},
            {"mortality":  4.3, "species": "Ulmus rubra Muhl."},
            {"mortality":  3.8, "species": "Liriodendron tulipifera L."},
            {"mortality":  3.6, "species": "Quercus rubra L."},
            {"mortality":  3.3, "species": "Prunus serotina Ehrh."},
            {"mortality":  3.2, "species": "Picea abies (L.) Karst."},
            {"mortality":  2.3, "species": "Fagus grandifolia Ehrh."},
            {"mortality":  1.3, "species": "Acer rubrum L."},
            {"mortality":  1.0, "species": "Quercus alba L."},
            {"mortality":  0.0, "species": "Carya tomentosa(Lam. ex Poir.) Nutt."},
            {"mortality":  0.0, "species": "Pinus strobus L."},
            {"mortality":  0.0, "species": "Cornus alternifolia L. f."},
            {"mortality":  0.0, "species": "Nyssa sylvatica Marsh."},
            {"mortality":  0.0, "species": "Carpinus caroliniana Walt."},
            ]
    },
    "references": [
        {
            "url": "http://www.treesearch.fs.fed.us/pubs/7005",
            "text": "Tree mortality data from tables 2, 3, and 4 of: D. J. Nowak, M. Kuroda, and D. E. Crane. Tree mortality rates and tree population projections in Baltimore, Maryland, USA. Urban Forestry & Urban Greening, 2(3):139-147, 2004. ISSN 1618-8667. doi: 10.1078/1618-8667-00030."
        },
        {
            "url": "http://www.nrs.fs.fed.us/pubs/gtr/gtr_ne186.pdf",
            "text": "Growth rates from table 5 of: E. McPherson, D. Nowak, and R. Rowntree. Chicago's urban forest ecosystem: results of the Chicago Urban Forest Climate Project, volume 186. US Dept. of Agriculture, Forest Service, Northeastern Forest Experiment Station, 1994."
        }
    ]
}




