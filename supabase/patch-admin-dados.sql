-- Gestao de dados (admin/secretaria) — antes do painel web
-- Desativar aluno, atualizar dados, foto_path, policy DELETE fotos

drop policy if exists fotos_apagar on storage.objects;
create policy fotos_apagar on storage.objects for delete to authenticated
  using (bucket_id = 'fotos' and public.meu_papel() in ('secretaria', 'admin'));

create or replace function public.definir_aluno_ativo(
  p_codigo_admin text,
  p_aluno_id uuid,
  p_ativo boolean,
  p_motivo text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_erro text;
  v_nome text;
begin
  if coalesce(public.meu_papel(), '') not in ('secretaria', 'admin') then
    raise exception 'Sem permissao.';
  end if;
  v_erro := public.verificar_codigo(p_codigo_admin);
  if v_erro is not null then
    return jsonb_build_object('ok', false, 'erro', v_erro);
  end if;

  select nome into v_nome from public.alunos where id = p_aluno_id;
  if v_nome is null then
    return jsonb_build_object('ok', false, 'erro', 'Aluno nao encontrado.');
  end if;

  update public.alunos set ativo = p_ativo where id = p_aluno_id;

  if not p_ativo then
    update public.passes
       set estado = 'bloqueado',
           motivo = coalesce(nullif(trim(p_motivo), ''), 'Aluno desativado')
     where aluno_id = p_aluno_id and estado = 'ativo';
  end if;

  perform public.registar_auditoria(
    case when p_ativo then 'aluno_reativado' else 'aluno_desativado' end,
    'alunos', p_aluno_id::text,
    jsonb_build_object('nome', v_nome, 'ativo', p_ativo, 'motivo', p_motivo)
  );

  return jsonb_build_object('ok', true, 'aluno_id', p_aluno_id, 'ativo', p_ativo);
end $$;

create or replace function public.atualizar_aluno(
  p_codigo_admin text,
  p_aluno_id uuid,
  p_nome text default null,
  p_turma text default null,
  p_curso text default null,
  p_ano_letivo text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_erro text;
begin
  if coalesce(public.meu_papel(), '') not in ('secretaria', 'admin') then
    raise exception 'Sem permissao.';
  end if;
  v_erro := public.verificar_codigo(p_codigo_admin);
  if v_erro is not null then
    return jsonb_build_object('ok', false, 'erro', v_erro);
  end if;

  if not exists (select 1 from public.alunos where id = p_aluno_id) then
    return jsonb_build_object('ok', false, 'erro', 'Aluno nao encontrado.');
  end if;

  update public.alunos set
    nome = coalesce(nullif(trim(p_nome), ''), nome),
    turma = coalesce(p_turma, turma),
    curso = coalesce(p_curso, curso),
    ano_letivo = coalesce(p_ano_letivo, ano_letivo)
  where id = p_aluno_id;

  perform public.registar_auditoria('aluno_atualizado', 'alunos', p_aluno_id::text,
    jsonb_build_object('nome', p_nome, 'turma', p_turma, 'curso', p_curso));

  return jsonb_build_object('ok', true, 'aluno_id', p_aluno_id);
end $$;

create or replace function public.definir_foto_aluno(
  p_codigo_admin text,
  p_aluno_id uuid,
  p_foto_path text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_erro text;
  v_antes text;
begin
  if coalesce(public.meu_papel(), '') not in ('secretaria', 'admin') then
    raise exception 'Sem permissao.';
  end if;
  v_erro := public.verificar_codigo(p_codigo_admin);
  if v_erro is not null then
    return jsonb_build_object('ok', false, 'erro', v_erro);
  end if;

  select foto_path into v_antes from public.alunos where id = p_aluno_id;
  if not found then
    return jsonb_build_object('ok', false, 'erro', 'Aluno nao encontrado.');
  end if;

  update public.alunos
     set foto_path = nullif(trim(p_foto_path), '')
   where id = p_aluno_id;

  perform public.registar_auditoria(
    case when nullif(trim(p_foto_path), '') is null then 'foto_removida' else 'foto_definida' end,
    'alunos', p_aluno_id::text,
    jsonb_build_object('antes', v_antes, 'depois', nullif(trim(p_foto_path), ''))
  );

  return jsonb_build_object(
    'ok', true,
    'aluno_id', p_aluno_id,
    'foto_path', nullif(trim(p_foto_path), ''),
    'foto_anterior', v_antes
  );
end $$;

do $$
declare r record;
begin
  for r in
    select p.oid::regprocedure as assinatura
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname in ('definir_aluno_ativo', 'atualizar_aluno', 'definir_foto_aluno')
  loop
    execute format('grant execute on function %s to authenticated', r.assinatura);
  end loop;
end $$;
