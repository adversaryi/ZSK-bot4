-- добавляем колонку пола
alter table trainers
    add column if not exists gender varchar(1);

-- добавляем constraint безопасно
do $$
    begin
        if not exists (
            select 1
            from pg_constraint
            where conname = 'chk_trainers_gender'
        ) then
            alter table trainers
                add constraint chk_trainers_gender
                    check (gender in ('M', 'F') or gender is null);
        end if;
    end $$;