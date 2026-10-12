-- Historico recente para o APK (staff autenticado)
create or replace function public.listar_movimentos_recentes(p_limite int default 50)
returns jsonb language plpgsql stable security definer set search_path = public as $$
begin
  if coalesce(public.meu_papel(), '') = '' then
    raise exception 'Sem permissao.';
  end if;
  return coalesce((
    select jsonb_agg(row_to_json(t)::jsonb order by t.ocorrido_em desc)
    from (
      select
        m.id,
        m.tipo,
        m.resultado,
        m.offline,
        m.ocorrido_em,
        a.nome as aluno_nome,
        a.turma as aluno_turma,
        a.curso as aluno_curso,
        a.codigo_id as aluno_codigo,
        a.foto_path,
        p.numero as numero_passe,
        p.estado as passe_estado
      from public.movimentos m
      left join public.alunos a on a.id = m.aluno_id
      left join public.passes p on p.id = m.passe_id
      order by m.ocorrido_em desc
      limit greatest(1, least(coalesce(p_limite, 50), 200))
    ) t
  ), '[]'::jsonb);
end $$;

grant execute on function public.listar_movimentos_recentes(int) to authenticated;
