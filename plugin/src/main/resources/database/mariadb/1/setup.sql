create table taerra_maps
(
    map_id binary(16)   not null,
    world  varchar(255) not null,
    view   varchar(16)  not null,
    primary key (map_id),
    unique (world, view)
) charset = utf8mb4
  collate = utf8mb4_bin;

create table taerra_tiles
(
    map_id   binary(16)  not null,
    rotation tinyint     not null,
    zoom     tinyint     not null,
    x        int         not null,
    z        int         not null,
    layer    varchar(64) not null,
    kind     tinyint     not null,
    data     mediumblob  not null,
    modified bigint      not null,
    primary key (map_id, rotation, zoom, x, z, layer, kind),
    index index_modified (map_id, modified)
) charset = ascii
  collate = ascii_bin;
