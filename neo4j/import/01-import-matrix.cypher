LOAD CSV WITH HEADERS FROM 'file:///matrix_characters.csv' AS row
WITH row WHERE row.character IS NOT NULL AND row.character <> ''
MERGE (c:Character {name: row.character})
SET c.roleNotes = row.role_notes
MERGE (a:Actor {name: row.actor})
SET a.born = toInteger(row.actor_born)
MERGE (m:Movie {title: row.movie})
SET m.released = toInteger(row.movie_released)
MERGE (a)-[:PLAYED]->(c)
MERGE (c)-[:APPEARS_IN]->(m);
