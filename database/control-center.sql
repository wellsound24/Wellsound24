create table public.w24_members (admin_id uuid primary key references public.admin_accounts(id), role text not null check(role in ('owner','editor')));
insert into public.w24_members select id,'owner' from public.admin_accounts where username='tonic' and enabled;
create table public.w24_state (id int primary key check(id=1), draft jsonb not null default '{}'::jsonb, published jsonb, revision int not null default 0, published_at timestamptz, updated_at timestamptz not null default now());
insert into public.w24_state(id) values(1);
create table public.w24_versions (id bigint generated always as identity primary key, content jsonb not null, admin_id uuid references public.admin_accounts(id), created_at timestamptz not null default now());
create table public.w24_leads (id uuid primary key default gen_random_uuid(), name text not null, contact text not null, message text not null, status text not null default 'new' check(status in ('new','contacted','closed')), created_at timestamptz not null default now());
create table public.w24_events (id bigint generated always as identity primary key, kind text not null check(kind in ('view','contact')), page text not null, created_at timestamptz not null default now());
create table public.w24_rate_limits (key text primary key, count int not null, expires_at timestamptz not null);
alter table public.w24_members enable row level security;
alter table public.w24_state enable row level security;
alter table public.w24_versions enable row level security;
alter table public.w24_leads enable row level security;
alter table public.w24_events enable row level security;
alter table public.w24_rate_limits enable row level security;
revoke all on public.w24_members,public.w24_state,public.w24_versions,public.w24_leads,public.w24_events,public.w24_rate_limits from anon,authenticated;
grant all on public.w24_members,public.w24_state,public.w24_versions,public.w24_leads,public.w24_events,public.w24_rate_limits to service_role;
grant usage, select on sequence public.w24_versions_id_seq, public.w24_events_id_seq to service_role;
create function public.w24_save(p_content jsonb,p_revision int,p_admin uuid,p_publish boolean default false) returns jsonb language plpgsql security invoker set search_path=public as $$
declare s public.w24_state;
begin
 select * into s from public.w24_state where id=1 for update;
 if s.revision<>p_revision then raise exception 'revision_conflict'; end if;
 if p_publish then
   insert into public.w24_versions(content,admin_id) values(p_content,p_admin);
   update public.w24_state set draft=p_content,published=p_content,revision=revision+1,published_at=now(),updated_at=now() where id=1 returning * into s;
 else
   update public.w24_state set draft=p_content,revision=revision+1,updated_at=now() where id=1 returning * into s;
 end if;
 return to_jsonb(s);
end $$;
revoke all on function public.w24_save(jsonb,int,uuid,boolean) from public,anon,authenticated;
grant execute on function public.w24_save(jsonb,int,uuid,boolean) to service_role;
create function public.w24_limit(p_key text,p_max int,p_seconds int) returns boolean language plpgsql security invoker set search_path=public as $$
declare n int;
begin
 insert into public.w24_rate_limits(key,count,expires_at) values(p_key,1,now()+make_interval(secs=>p_seconds)) on conflict(key) do update set count=case when w24_rate_limits.expires_at<now() then 1 else w24_rate_limits.count+1 end,expires_at=case when w24_rate_limits.expires_at<now() then now()+make_interval(secs=>p_seconds) else w24_rate_limits.expires_at end returning count into n;
 return n<=p_max;
end $$;
revoke all on function public.w24_limit(text,int,int) from public,anon,authenticated;
grant execute on function public.w24_limit(text,int,int) to service_role;
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types) values('w24-media','w24-media',true,20971520,array['image/jpeg','image/png','image/webp','image/gif','video/mp4','video/webm']);
