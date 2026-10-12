-- Leitura unificada: token 32hex OU codigo_id OU numero passe
-- Propina vencida = aviso (permitido + estado_propina)

create or replace function public.registar_leitura(
  p_evento_id uuid,
  p_token text default null,
  p_codigo text default null,
  p_tipo text default 'entrada',
  p_ocorrido_em timestamptz default now(),
  p_offline boolean default false,
  p_decisao_local text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_cfg public.config%rowtype;
  v_passe public.passes%rowtype;
  v_aluno public.alunos%rowtype;
  v_quando timestamptz;
  v_dia date;
  v_prop text;
  v_aval text;
  v_res text;
  v_scan text;
begin
  if coalesce(public.meu_papel(), '') = '' then
    raise exception 'Sem permissao.';
  end if;
  if p_evento_id is null then
    raise exception 'p_evento_id e obrigatorio.';
  end if;
  if p_tipo not in ('entrada', 'saida') then
    raise exception 'Tipo invalido.';
  end if;

  if exists (select 1 from public.movimentos where id = p_evento_id) then
    return public.resposta_movimento(p_evento_id);
  end if;

  select * into v_cfg from public.config where id = 1;
  v_quando := least(coalesce(p_ocorrido_em, now()), now() + interval '5 minutes');
  v_dia := (v_quando at time zone 'Africa/Luanda')::date;
  v_scan := nullif(trim(coalesce(p_token, p_codigo, '')), '');

  if v_scan is null then
    insert into public.movimentos (id, token_parcial, tipo, ocorrido_em, scanner_id, offline, resultado)
    values (p_evento_id, null, p_tipo, v_quando, auth.uid(), p_offline, 'desconhecido');
    return public.resposta_movimento(p_evento_id);
  end if;

  if v_scan ~ '^[a-fA-F0-9]{32}$' then
    select * into v_passe from public.passes where token = lower(v_scan);
  elsif v_scan ~ '^[0-9]{1,9}$' then
    select * into v_passe from public.passes where numero = v_scan::int;
  else
    select p.* into v_passe
      from public.alunos a
      join public.passes p on p.aluno_id = a.id
     where a.codigo_id = upper(trim(v_scan))
     order by (p.estado = 'ativo') desc, p.emitido_em desc
     limit 1;
  end if;

  if not found then
    insert into public.movimentos (id, token_parcial, tipo, ocorrido_em, scanner_id, offline, resultado)
    values (p_evento_id, left(v_scan, 12), p_tipo, v_quando, auth.uid(), p_offline, 'desconhecido');
    return public.resposta_movimento(p_evento_id);
  end if;

  select * into v_aluno from public.alunos where id = v_passe.aluno_id;
  v_prop := public.estado_propina(v_aluno.vencimento, v_dia);
  perform pg_advisory_xact_lock(hashtextextended(v_passe.id::text, 0));

  if exists (select 1 from public.movimentos
              where passe_id = v_passe.id and tipo = p_tipo
                and resultado in ('permitido', 'autorizado_manual', 'pendente_autorizacao')
                and ocorrido_em between v_quando - make_interval(secs => v_cfg.duplicado_segundos) and v_quando) then
    insert into public.movimentos (id, passe_id, aluno_id, tipo, ocorrido_em, scanner_id, offline, resultado)
    values (p_evento_id, v_passe.id, v_aluno.id, p_tipo, v_quando, auth.uid(), p_offline, 'duplicado');
    return public.resposta_movimento(p_evento_id);
  end if;

  if v_passe.estado <> 'ativo' or not v_aluno.ativo then
    v_aval := 'negado_passe';
  else
    v_aval := 'permitido';
  end if;

  if not p_offline then
    v_res := v_aval;
  elsif p_tipo = 'saida' then
    v_res := 'permitido';
  elsif p_decisao_local in ('permitido', 'negado_passe', 'negado_propina') then
    v_res := p_decisao_local;
  else
    v_res := 'pendente_autorizacao';
  end if;

  insert into public.movimentos (id, passe_id, aluno_id, tipo, ocorrido_em, scanner_id,
                                 offline, resultado, avaliacao_servidor)
  values (p_evento_id, v_passe.id, v_aluno.id, p_tipo, v_quando, auth.uid(),
          p_offline, v_res, v_aval);

  return public.resposta_movimento(p_evento_id);
end $$;

create or replace function public.resposta_movimento(p_id uuid)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'ok', true,
    'movimento_id', m.id,
    'tipo', m.tipo,
    'resultado', m.resultado,
    'avaliacao_servidor', m.avaliacao_servidor,
    'mensagem', case m.resultado
      when 'permitido' then case m.tipo when 'entrada' then 'ENTRADA AUTORIZADA' else 'SAIDA REGISTADA' end
      when 'autorizado_manual' then 'ENTRADA AUTORIZADA PELA ADMINISTRACAO'
      when 'negado_propina' then 'ATENCAO: PROPINA VENCIDA. Verifique com a secretaria.'
      when 'negado_passe' then 'ENTRADA RECUSADA: PASSE BLOQUEADO OU INATIVO'
      when 'pendente_autorizacao' then 'EVENTO GUARDADO OFFLINE — VERIFICACAO PENDENTE'
      when 'duplicado' then 'LEITURA REPETIDA IGNORADA'
      else 'QR / CODIGO DESCONHECIDO'
    end,
    'aluno', case when a.id is null then null else jsonb_build_object(
      'nome', a.nome, 'turma', a.turma, 'curso', a.curso,
      'codigo_id', a.codigo_id, 'foto_path', a.foto_path,
      'numero_passe', p.numero, 'vencimento', a.vencimento
    ) end,
    'estado_propina', case when a.id is null then null else
      public.estado_propina(a.vencimento, (m.ocorrido_em at time zone 'Africa/Luanda')::date) end,
    'passe_estado', p.estado,
    'offline', m.offline,
    'ocorrido_em', m.ocorrido_em
  )
  from public.movimentos m
  left join public.passes p on p.id = m.passe_id
  left join public.alunos a on a.id = m.aluno_id
  where m.id = p_id
$$;
