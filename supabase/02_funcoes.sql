-- 2. FUNÇÕES AUXILIARES
-- ---------------------------------------------------------------------

create or replace function public.hoje_luanda()
returns date language sql stable as $$
  select (now() at time zone 'Africa/Luanda')::date
$$;

create or replace function public.estado_propina(p_vencimento date, p_dia date default public.hoje_luanda())
returns text language sql stable as $$
  select case
    when p_vencimento is null then 'sem_pagamento'
    when p_dia > p_vencimento then 'vencida'
    else 'em_dia'
  end
$$;

create or replace function public.meu_papel()
returns text language sql stable security definer set search_path = public as $$
  select papel from public.staff where id = auth.uid() and ativo
$$;

create or replace function public.registar_auditoria(
  p_acao text, p_entidade text, p_entidade_id text, p_detalhes jsonb default null)
returns void language sql security definer set search_path = public as $$
  insert into public.auditoria (staff_id, acao, entidade, entidade_id, detalhes)
  values (auth.uid(), p_acao, p_entidade, p_entidade_id, p_detalhes)
$$;

-- devolve NULL se o código está certo, ou a mensagem de erro.
-- Não levanta exceção para a tentativa falhada ficar gravada (limite: 5 em 15 min).
create or replace function public.verificar_codigo(p_codigo text)
returns text language plpgsql security definer set search_path = public, extensions as $$
declare
  v_hash text;
begin
  if (select count(*) from public.codigo_falhas
       where staff_id = auth.uid() and quando > now() - interval '15 minutes') >= 5 then
    return 'Demasiadas tentativas erradas. Aguarde 15 minutos.';
  end if;

  select codigo_admin_hash into v_hash from public.config where id = 1;
  if v_hash is null then
    return 'O código de administração ainda não foi definido.';
  end if;

  if p_codigo is null or crypt(p_codigo, v_hash) <> v_hash then
    insert into public.codigo_falhas (staff_id) values (auth.uid());
    return 'Código de administração incorreto.';
  end if;

  return null;
end $$;

-- número de passe sem buracos: 1, 2, 3... (a transação desfeita não gasta número)
create or replace function public.novo_passe(p_aluno uuid)
returns public.passes language plpgsql security definer set search_path = public as $$
declare
  v_num int;
  v_passe public.passes;
begin
  select proximo_passe into v_num from public.config where id = 1 for update;
  if v_num > 999999999 then
    raise exception 'Limite de 999999999 passes atingido.';
  end if;
  update public.config set proximo_passe = v_num + 1 where id = 1;

  insert into public.passes (numero, aluno_id, emitido_por)
  values (v_num, p_aluno, auth.uid())
  returning * into v_passe;
  return v_passe;
end $$;
