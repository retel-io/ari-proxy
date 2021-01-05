CREATE KEYSPACE retel with replication = {'class':'SimpleStrategy','replication_factor':1};

USE retel;

CREATE TABLE retel (
"key" text primary key,
"value" text
);

