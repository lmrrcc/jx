function List({ items, keyOf, render }) {
  return <ul>{items.map(item => <li key={keyOf(item)}>{render(item)}</li>)}</ul>;
}

export function GenericList() {
  return (
    <main>
      <List items={['x', 'y']} keyOf={item => item} render={item => item.toUpperCase()} />
      <List items={[{ id: 1, name: 'one' }, { id: 2, name: 'two' }]} keyOf={item => item.id} render={item => item.id + ':' + item.name} />
    </main>
  );
}
