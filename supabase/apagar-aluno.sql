-- Apagar aluno DEFINITIVAMENTE (com codigo admin)

create or replace function public.apagar_aluno(
  p_codigo_admin text,
  p_aluno_id uuid
) returns jsonb
language plpgsql
security definer
set search_path = public
as $body$
declare
  v_erro text;
  v_nome text;
  v_codigo text;
begin
  if coalesce(public.meu_papel(), '') not in ('secretaria', 'admin') then
    raise exception 'Sem permissao.';
  end if;
  v_erro := public.verificar_codigo(p_codigo_admin);
  if v_erro is not null then
    return jsonb_build_object('ok', false, 'erro', v_erro);
  end if;

  select nome, codigo_id into v_nome, v_codigo
  from public.alunos where id = p_aluno_id;
  if v_nome is null then
    return jsonb_build_object('ok', false, 'erro', 'Aluno nao encontrado.');
  end if;

  delete from public.movimentos where aluno_id = p_aluno_id;
  delete from public.pagamentos where aluno_id = p_aluno_id;
  delete from public.passes where aluno_id = p_aluno_id;
  delete from public.alunos where id = p_aluno_id;

  perform public.registar_auditoria(
    'aluno_apagado',
    'alunos',
    p_aluno_id::text,
    jsonb_build_object('nome', v_nome, 'codigo_id', v_codigo)
  );

  return jsonb_build_object('ok', true, 'nome', v_nome, 'codigo_id', v_codigo);
end
$body$;

grant execute on function public.apagar_aluno(text, uuid) to authenticated;
