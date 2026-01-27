create extension if not exists "uuid-ossp";

create table if not exists trainers (
                                        id uuid primary key,
                                        name text not null,
                                        description text not null,
                                        telegram_username text not null,
                                        photo_file_id text not null,
                                        created_at timestamptz not null default now()
);

create index if not exists idx_trainers_created_at
    on trainers(created_at desc);