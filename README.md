# SapFhir-CLI

Join genomic variation graphs with public data or internal medical data e.g. FHIR.
by having a FAIR data access, using W3C sparql as a standard protocol.

This is a command line tool to quickly test the concept.


# Building

You need local checkouts of 

[handlegraph4j](https://github.com/JervenBolleman/handlegraph4j)
[handlegraph4jGFA](https://github.com/JervenBolleman/handlegraph4jGFA)
[handlegraph4jRDF](https://github.com/JervenBolleman/handlegraph4jRDF)
[handlegraph4j-simple](https://github.com/JervenBolleman/handlegraph4j-simple)
[sapfhir](https://github.com/JervenBolleman/sapfhir)

Build them with maven on java 11+

```
for i in handlegraph4j handlegraph4jGFA handlegraph4jRDF handlegraph4j-simple sapfhir
cd $i
    mvn install
cd ..
```

```
mvn assembly:assembly
```

# Run

```
java -jar target/sapfhir-cli-1.0-SNAPSHOT-jar-with-dependencies.jar \
    --gfa ~/git/odgi/test/t.gfa \
    -Xmx12g \
    "PREFIX vg:<http://biohackathon.org/resource/vg#> SELECT ?path WHERE {?path a vg:Path}"
```

# Status

This is a [RDF4j](https://rdf4j.org/) SAIL implementation that can take any handlegraph4j 
implementation and represent it as a [W3C sparql 1.1](https://www.w3.org/TR/sparql11-query/) endpoint. 

It is believed to be functionally complete.
