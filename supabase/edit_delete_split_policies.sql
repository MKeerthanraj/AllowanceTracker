-- Policies for the edit/delete-split feature.
-- Any member of the expense's group may edit or delete the expense, its splits,
-- its items, and each item's participants.
--
-- Run this once in the Supabase SQL editor. Without these, PostgREST silently
-- "succeeds" while deleting/updating nothing, which is exactly the
-- "missing DELETE permission on expense_items" error the app raises.

-- expenses: edit (reason/amount/split_type/subtotal/tax_amount) and delete
create policy "Group members can update expenses"
on public.expenses for update to authenticated
using (
  exists (
    select 1 from public.group_members gm
    where gm.group_id = expenses.group_id
      and gm.user_id = auth.uid()
  )
)
with check (
  exists (
    select 1 from public.group_members gm
    where gm.group_id = expenses.group_id
      and gm.user_id = auth.uid()
  )
);

create policy "Group members can delete expenses"
on public.expenses for delete to authenticated
using (
  exists (
    select 1 from public.group_members gm
    where gm.group_id = expenses.group_id
      and gm.user_id = auth.uid()
  )
);

-- expense_splits: the app upserts changed amounts (INSERT ... ON CONFLICT UPDATE,
-- which needs an UPDATE policy) and deletes rows for people removed from a split.
create policy "Group members can update splits"
on public.expense_splits for update to authenticated
using (
  exists (
    select 1
    from public.expenses e
    join public.group_members gm on gm.group_id = e.group_id
    where e.id = expense_splits.expense_id
      and gm.user_id = auth.uid()
  )
)
with check (
  exists (
    select 1
    from public.expenses e
    join public.group_members gm on gm.group_id = e.group_id
    where e.id = expense_splits.expense_id
      and gm.user_id = auth.uid()
  )
);

create policy "Group members can delete splits"
on public.expense_splits for delete to authenticated
using (
  exists (
    select 1
    from public.expenses e
    join public.group_members gm on gm.group_id = e.group_id
    where e.id = expense_splits.expense_id
      and gm.user_id = auth.uid()
  )
);

-- expense_items: re-saving a receipt replaces the item rows wholesale.
create policy "Group members can delete expense items"
on public.expense_items for delete to authenticated
using (
  exists (
    select 1
    from public.expenses e
    join public.group_members gm on gm.group_id = e.group_id
    where e.id = expense_items.expense_id
      and gm.user_id = auth.uid()
  )
);

-- expense_item_participants: these reference expense_items with no ON DELETE
-- CASCADE, so the app deletes them first; it needs permission to do so.
create policy "Group members can delete item participants"
on public.expense_item_participants for delete to authenticated
using (
  exists (
    select 1
    from public.expense_items i
    join public.expenses e on e.id = i.expense_id
    join public.group_members gm on gm.group_id = e.group_id
    where i.id = expense_item_participants.item_id
      and gm.user_id = auth.uid()
  )
);

-- OPTIONAL, only if deleting an old expense ever fails with a foreign-key error
-- from personal_transactions (the app no longer writes source_expense_id, but
-- old rows may still point at an expense):
--
-- alter table public.personal_transactions
--   drop constraint personal_transactions_source_expense_id_fkey;
-- alter table public.personal_transactions
--   add constraint personal_transactions_source_expense_id_fkey
--   foreign key (source_expense_id) references public.expenses(id)
--   on delete set null;
