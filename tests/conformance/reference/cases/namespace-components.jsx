const Layout = {
  Panel({ title, children }) {
    return (
      <section aria-label={title}>
        <h2>{title}</h2>
        {children}
      </section>
    );
  }
};

export function NamespaceComponents() {
  return (
    <main>
      <Layout.Panel title="Qualified">
        <p data-testid="inside">inside</p>
      </Layout.Panel>
    </main>
  );
}
