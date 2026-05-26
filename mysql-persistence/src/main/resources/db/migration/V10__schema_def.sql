CREATE TABLE meta_schema_def (
  id INT NOT NULL AUTO_INCREMENT,
  created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  modified_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  org_id varchar(255) NOT NULL,
  name varchar(255) NOT NULL,
  version int NOT NULL,
  json_data TEXT NOT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX unique_schema_def_org_name_version ON meta_schema_def (org_id, name, version);
CREATE INDEX schema_def_org_name_index ON meta_schema_def (org_id, name);
