-- Extend the existing private customer records; no website content is changed.
alter table public.w24_leads
 add column crm_status text not null default 'new' check (crm_status in ('new','waiting','quoted','confirmed','lost','needs_review')),
 add column event_name text not null default '',
 add column event_date date,
 add column location text not null default '',
 add column amount numeric(14,2) check (amount >= 0),
 add column deposit_status text not null default 'unverified' check (deposit_status in ('unverified','pending','received')),
 add column source text not null default 'unknown' check (source in ('unknown','website','line','phone','google_ads','facebook','other')),
 add column campaign text not null default '',
 add column attribution_evidence text not null default '',
 add column evidence jsonb not null default '[]'::jsonb check (jsonb_typeof(evidence)='array' and jsonb_array_length(evidence)<=50),
 add column revision integer not null default 0;
update public.w24_leads set crm_status=case status when 'contacted' then 'waiting' when 'closed' then 'needs_review' else 'new' end;
-- Old "closed" records are not proof of a confirmed booking.
create index w24_leads_crm_status_created on public.w24_leads(crm_status,created_at desc);
alter table public.w24_leads enable row level security;
revoke all on public.w24_leads from anon,authenticated;
create function public.w24_crm_summary() returns jsonb language sql stable security invoker set search_path=public as $$
 select jsonb_build_object('total',count(*),'new',count(*) filter(where crm_status='new'),'waiting',count(*) filter(where crm_status in ('waiting','quoted')),'confirmed',count(*) filter(where crm_status='confirmed'),'needs_review',count(*) filter(where crm_status='needs_review'),'value',coalesce(sum(amount) filter(where crm_status='confirmed'),0),'unpriced',count(*) filter(where crm_status='confirmed' and amount is null)) from public.w24_leads;
$$;
revoke all on function public.w24_crm_summary() from public,anon,authenticated;
grant execute on function public.w24_crm_summary() to service_role;
