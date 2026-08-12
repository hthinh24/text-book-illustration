# Domain — Book-to-Illustration Pipeline

## What this app does

Turns the text of one book into a set of AI-generated illustrations. A user pastes or uploads a book's text, then walks it through a 5-step pipeline, one step at a time, each step producing content that the next step depends on.

## The pipeline

| Step | Produces | Depends on |
|---|---|---|
| 1. Style | An art style for the whole book (user-supplied, or AI-generated from the book text) | Book text |
| 2. Character | Up to 2 adult characters, each with a name + image prompt | Style |
| 3. Portrait | One portrait image per character | Character prompts |
| 4. Chapter | Up to 1 chapter illustration prompt, referencing the characters | Characters + portraits |
| 5. Illustration | One illustration per chapter, reusing portraits so characters stay visually consistent | Chapter prompts + portraits |

Steps are **strictly sequential** — a project always sits at exactly one step, and that step must be fully done before the next one can start. There's no branching and no going back once a step is done.

## Domain rules (business rules, not implementation detail)

- **Caps:** max 2 characters, max 1 chapter per project. Enforced server-side, not just hidden in the UI.
- **Style is the only step with optional user input** — every other step's content is entirely AI-generated.
- **Character consistency across illustrations** is achieved by reusing the same portrait image reference in every illustration a character appears in — not by regenerating the character each time.
- **One project = one book.** No cross-project sharing of characters or style.
- **A project has one owner** (identified by email), and belongs to exactly one user.

## Entities and what they represent

- **User** — identified by email. No real authentication; email is a lookup key, not a login.
- **Project** — one book's pipeline run. Tracks which step it's on and that step's status. This is the aggregate root — almost everything else hangs off a Project.
- **Character** — an adult character in the book, with a name, an image prompt, and (once generated) a portrait.
- **Chapter** — one chapter's illustration: a prompt plus (once generated) the illustration image. Note: despite the name, this models "one illustrated moment," not the book's actual chapter structure — the cap of 1 keeps this scoped for the assessment, not a modeling claim that books have one chapter.
- **Character↔Chapter** — a chapter's illustration can reference more than one character (many-to-many).

## Why the pipeline is modeled as project-level state, not per-step rows

The domain doesn't need step history — once a step is done, nothing about its past retries or failures matters to the user or the pipeline going forward. What matters is: *what step am I on, and is it running, waiting, done, or failed right now.* That's a single current state, not a log — which is why the data model tracks one `step` / `step_status` per project rather than a row per step attempt. (See DECISIONS.md #2 for the tradeoff.)

## What "done" means for a project

A project is `DONE` once the ILLUSTRATION step's `step_status` is `SUCCESS` for the last (and only) chapter. There's no separate "publish" or "export" concept in this domain — finishing the pipeline is the end state.

## Out of scope for this domain (explicitly, per assessment spec)

- Multi-book projects, character reuse across books
- Any chapter structure beyond "1 illustrated chapter"
- Real user accounts / auth
- Any notion of editing or regenerating a step's output after the pipeline has moved past it (only retry-on-failure exists, not retroactive edits)
