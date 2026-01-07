Equipment management

When user clicks on a slot in Setup mode - it binds item to that slot, but he can click again - then this slot should have wide green border - an equipment slot. Equipment slots act mostly the same way as green refill slots, but when an item from it gets unequipped it should be placed into equipment chests or armor stands (see below) - not to output chest.

Left and right chest next to Wardrobe should play special role - equipment storage. Whenever an equipment slot needs to be emptied - it should be unloaded to equipment storage not to output chest. These chests can be big chests too. Unload equipment from equipment slots to those storages, then use those chests as input chests normally.

When unloading to equipment chests first try to unload to corresponding inventory line of equipment chest: first line in left chest is setup1, second line in left chest is setup2, setup3 is line 3, then setup4 corresponds to line1 of right chest, setup5 - to line2 and finally setup6 is to line3 in right chest.
If chests are big - more than 3 lines, then all space below those 3 lines should be used for storing equipment items that have no space in corresponding lines for them.
If there is not enough space - then put items into any equipment chest into any unoccupied slot.

Logic for equipment items:
- Scan all setups and find items that are in minecraft equipment inventory slots or corresponding slots in wardrobe or inside curios inventory slots (later) or items with wide green border - equipment mode slots.
- If an item is in one of those slots in any setup - it is considered "equipment" and should not go to output chest, it can only go to equipment chest.
- If there is not enough space for an item then unloading process for that cell should be aborted and a message should be printed to bottom line of wardrobe UI: "Not enough equipment space!"

Unloading triggers:
- Unloading might happen when user switches between setups - to do it an "E" button should be added near each setup in Operational mode.
- Also below all setups should be "Unload all" - which unloads all items and equipment from player, from all his slots.

Armor stands:
- Scan up to 16 blocks (5 blocks by default) around wardrobe to find all armor stands. Scan only when Wardrobe is put into setup mode or when scan range is changed.
- Scan range should be changeable in setup mode - add "Settings" button and dialog under "Fast transfer" button.
- Use those stands to store armor and equipment. Nearest stands correspond to setups with fewer numbers. Whole stand is bound to one setup slot - store stand position, so it won't change later.
- Armor stands are priority storage for equipment - unload to them first (if enough space) and only after it - to chests.
