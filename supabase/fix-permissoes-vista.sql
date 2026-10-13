-- Corrige: permission denied for view vw_estado_alunos
grant select on public.vw_estado_alunos to authenticated;
grant select on public.alunos to authenticated;
grant select on public.passes to authenticated;
grant select on public.pagamentos to authenticated;
grant select on public.movimentos to authenticated;
grant select on public.staff to authenticated;

create or replace view public.vw_estado_alunos
with (security_invoker = true) as
select a.id as aluno_id, a.nome, a.codigo_id, a.turma, a.ano_letivo, a.curso, a.ativo,
       a.vencimento,
       public.estado_propina(a.vencimento) as estado_propina,
       case when a.vencimento is null then null
            else a.vencimento - public.hoje_luanda() end as dias_para_vencer,
       p.id as passe_id, p.numero as passe_numero, p.estado as passe_estado
from public.alunos a
left join public.passes p on p.aluno_id = a.id and p.estado = 'ativo';
