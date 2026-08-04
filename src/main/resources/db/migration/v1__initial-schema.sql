CREATE TABLE discord_link
(
    minecraft_uuid UUID PRIMARY KEY,
    discord_id VARCHAR(255) NOT NULL
);

CREATE TABLE discord_link_request_code
(
    minecraft_uuid UUID PRIMARY KEY,
    request_code VARCHAR(255) NOT NULL
);