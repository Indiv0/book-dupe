package in.nikitapek.bookdupe.events;

import in.nikitapek.bookdupe.util.BookDupeConfigurationContext;

import java.util.HashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;

public final class BookDupeListener implements Listener {
    private final BookDupeConfigurationContext configurationContext;

    public BookDupeListener(final BookDupeConfigurationContext configurationContext) {
        this.configurationContext = configurationContext;
    }

    @EventHandler
    public void onItemCraft(final CraftItemEvent event) {
        // Get the crafting inventory (3x3 matrix) used to craft the item.
        final CraftingInventory craftingInventory = event.getInventory();

        // Get the index of the first (and only) Material.WRITTEN_BOOK used in
        // the recipe.
        final int writtenBookIndex = craftingInventory.first(Material.WRITTEN_BOOK);

        // Makes sure the recipe contains a WRITTEN_BOOK.
        if (writtenBookIndex == -1) {
            return;
        }

        // If the player does not have permissions to copy books, cancels the
        // event.
        if (!event.getWhoClicked().hasPermission("bookdupe.use")) {
            event.setCancelled(true);
            return;
        }

        // ItemStack represention of the book to be cloned.
        final ItemStack initialBook = craftingInventory.getItem(writtenBookIndex);

        // Gets the BookMeta data of the book.
        final BookMeta book = (BookMeta) initialBook.getItemMeta();

        // If the player does not have permission to copy any book
        // and the book was not written by the player, do not allow
        // the player to copy the book.
        if (!event.getWhoClicked().hasPermission("bookdupe.any")
                && !book.getAuthor().equals(event.getWhoClicked().getName())) {
            event.setCancelled(true);
            return;
        }

        // If the book has enchantments, check to see whether or not they're
        // allowed.
        if (!initialBook.getEnchantments().isEmpty() && !configurationContext.allowIllegalEnchants) {
            event.setCancelled(true);
            return;
        }

        // Get the player's inventory.
        final PlayerInventory playerInventory = event.getWhoClicked().getInventory();

        // Gets the index of the first INK_SACK in the recipe.
        final int inkSackIndex = craftingInventory.first(Material.INK_SACK);
        // Gets the index of the first FEATHER in the recipe.
        final int featherIndex = craftingInventory.first(Material.FEATHER);
        // Gets the index of the first BOOK in the recipe.
        final int bookIndex = craftingInventory.first(Material.BOOK);

        if (inkSackIndex != -1 && featherIndex != -1 && bookIndex == -1) {
            event.setCurrentItem(getNewBook(initialBook, Material.BOOK_AND_QUILL));
        } else if (inkSackIndex == -1 || featherIndex == -1 || bookIndex == -1) {
            // Check only one BOOK_AND_QUILL is in the crafting matrix.
            if (craftingInventory.all(Material.BOOK_AND_QUILL).size() != 2) {
                return;
            }

            // Adds the original book to the player's inventory.
            playerInventory.addItem(initialBook);

            // Sets the result of the craft to the copied books.
            event.setCurrentItem(getNewBook(initialBook, Material.WRITTEN_BOOK));
        } else {
            // Handle a non BOOK_AND_QUILL based recipe.
            // If the player regularly clicked (singular craft).
            if (!event.isShiftClick()) {
                // Adds the original book to the player's inventory.
                playerInventory.addItem(getNewBook(initialBook, Material.WRITTEN_BOOK));
            } else {
                // Gets the amount of INK_SACK in the crafting matrix.
                final int inkSackAmount = craftingInventory.getItem(inkSackIndex).getAmount();
                // Gets the amount of FEATHER in the crafting matrix.
                final int featherAmount = craftingInventory.getItem(featherIndex).getAmount();
                // Gets the amount of BOOK in the crafting matrix.
                final int bookAmount = craftingInventory.getItem(bookIndex).getAmount();

                int lowestAmount = 0;

                // Get the ingredient of which there is the least and loop until
                // that ingredient no longer exists.
                if (inkSackAmount < featherAmount && inkSackAmount < bookAmount) {
                    lowestAmount = inkSackAmount;
                }
                // Otherwise check if the crafting inventory contains less
                // FEATHER than any other ingredient.
                if (featherAmount < inkSackAmount && featherAmount < bookAmount) {
                    lowestAmount = featherAmount;
                    // Otherwise the crafting inventory contains less BOOK than
                    // any
                    // other ingredient.
                } else {
                    lowestAmount = bookAmount;
                }

                // Loops through crafting matrix reducing item amounts
                // one-by-one.
                int itemsLeft = 0;

                itemsLeft = craftingInventory.getItem(inkSackIndex).getAmount()
                        - lowestAmount;

                if (itemsLeft != 0) {
                    craftingInventory.getItem(inkSackIndex).setAmount(itemsLeft);
                } else {
                    craftingInventory.clear(inkSackIndex);
                }

                itemsLeft = craftingInventory.getItem(featherIndex).getAmount()
                        - lowestAmount;

                if (itemsLeft != 0) {
                    craftingInventory.getItem(featherIndex).setAmount(itemsLeft);
                } else {
                    craftingInventory.clear(featherIndex);
                }

                itemsLeft = craftingInventory.getItem(bookIndex).getAmount()
                        - lowestAmount;

                if (itemsLeft != 0) {
                    craftingInventory.getItem(bookIndex).setAmount(itemsLeft);
                } else {
                    craftingInventory.clear(bookIndex);
                }

                // Creates a HashMap to store items which do not fit into the
                // player's inventory.
                final HashMap<Integer, ItemStack> leftOver = new HashMap<Integer, ItemStack>();

                // Adds the new books to the player's inventory.
                for (int i = 0; i < lowestAmount; i++) {
                    leftOver.putAll((playerInventory.addItem(getNewBook(initialBook, Material.WRITTEN_BOOK))));

                    if (leftOver.isEmpty()) {
                        continue;
                    }

                    final Location loc = event.getWhoClicked().getLocation();
                    final ItemStack item = getNewBook(initialBook, Material.WRITTEN_BOOK);
                    event.getWhoClicked().getWorld().dropItem(loc, item);
                }
            }

            // Sets the result of the craft to the copied books.
            event.setCurrentItem(initialBook);
        }
    }

    private ItemStack getNewBook(final ItemStack previousBook, final Material bookType) {
        if (bookType == null || (bookType != Material.WRITTEN_BOOK && bookType != Material.BOOK_AND_QUILL)) {
            throw new IllegalArgumentException();
        }
        // Creates the new book to be returned.
        final ItemStack newBook = new ItemStack(bookType);

        // Retrieves the BookMeta data.
        final BookMeta newBookMeta = (BookMeta) newBook.getItemMeta();
        final BookMeta previousBookMeta = (BookMeta) previousBook.getItemMeta();

        // Transfers the author, title, and pages to the new tag.
        newBookMeta.setAuthor(previousBookMeta.getAuthor());
        newBookMeta.setTitle(previousBookMeta.getTitle());
        newBookMeta.setLore(previousBookMeta.getLore());
        newBookMeta.setPages(previousBookMeta.getPages());

        // If the transfer of enchantments is allowed, transfers them.
        if (configurationContext.allowIllegalEnchantTransfer && previousBookMeta.hasEnchants()) {
            newBookMeta.getEnchants().putAll(previousBookMeta.getEnchants());
        }

        newBook.setItemMeta(newBookMeta);
        return newBook;
    }
}