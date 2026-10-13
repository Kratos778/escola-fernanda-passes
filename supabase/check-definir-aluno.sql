-- Verifica se existe a funcao de desactivar aluno
select proname from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and p.proname = 'definir_aluno_ativo';
