#!/usr/bin/env python3
"""Direct ore leaching and the powered three-ingot route."""
import json
from pathlib import Path
import generate_petrochemical_ponder as p
from generate_electrolysis_ponder import unit
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/create_refinery_n_reactors';NS='create_refinery_n_reactors:'

def main():
 p.OUT=A/'ponder/plant';s,*_=unit(length=2,width=13,origin=(5,2,5));s.save('electrowinning')
 from generate_wet_processing import ponder as wet_ponder
 wet_ponder()

if __name__=='__main__':main()
