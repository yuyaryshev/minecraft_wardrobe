[[Minecraft - моя сборка]]

Please help me making new minecraft 1.20.1 forge mod. Place it into: `D:\b\Mine\GIT_Work\minecraft\mods_2025-12-23\minecraft_wardrobe` Please use `D:\b\Mine\GIT_Work\minecraft\mods_2025-12-23\minecraft_mechanical_workbench` mod as template for gradle and all the rest setup (but please do remove all code related to that mod, after you copy it). Then please implement the mod according to the following specs in "D:\b\InfoVault\Minecraft - Wardrobe mod.md" - copy that specs to docs/ when you create it.
Record your progress into docs/llm_plan.md with status (not started -> not tested by llm -> not tested by human -> finished) for each feature.
Please also try to use this mcp service to test D:\b\Mine\GIT_Work\minecraft\mods_2025-12-23\minecraft_mcp_for_llm\docs\llm_instruction.md
Always after you done implementing - try to compile and test. After done everything - please report.
# Minecraft Wardrobe mod
Main idea of the mod is to allow player to easily get rid of unwanted items from inventory and refill items that he needs. To do this a new block is created.
- This block's model is actually 2 blocks tall when placed.
- The block should hold all it's data in it's own NBT when it's destroyed and becomes an item and when it's placed back.
- Wardrobe is crafted from Planks and Chests: 'PCP', 'PCP', 'CCC'.
- Behind that block up to 16 WardrobeConnection blocks can be placed - these connect wardrobe to chests or any other input inventories
	- WardrobeConnection is crafted with 'PCP', 'PCP', 'PCP' pattern from Planks and Rods
- Under wardrobe and additional output inventory can be placed
- When played right-click Wardrobe UI should be open. This UI resembles normal inventory, mimicing all it's slots, including main inventory, hotbar, armor, shield and curios slots.
- Wardrobe UI have two modes "Setup" and "Operational", user can switch between them with a button on top right. This two UI modes should have different background.
- Setup mode allows player to setup wardrobe he likes. In this mode no items is actually moved or consume, in this mode user only binds and sets up wardrobe.
- Each Wardrobe have 8 setups in it. User can switch between them using 8 buttons below "Setup/operational" button. Each that button have a decorative item slot to the left of it. In set up mode user can set that slot to any item he likes. In operational mode clicking that slot have same effect as clicking the button. Button name can be edited in Setup mode, so user can give a name to each of that setups.
- 
- 1) User have to preset item, to do this he can
	- Pick an item from inventory then - click on a slot - this binds that slot to specified item.
	- Shift + right clicking on a slot changes that slot mode between
		- Blue mode - unload and load mode - default mode for bound slot (unbound slot have no special border)
		- Red mode - unload only mode
		- Green mode - load only mode
	- Right clicking on a slot - clears pervious binding
- 2) After slot is bound if it's bound to a stackable item, then
		- Right half of that slot have two special parts - right top quarter and right bottom quarter. 
		- In right top quarter max amount of item is set, in bottom quoter - minimum amount
	- Left click - increases count of items by 1
	- Right click - decreases count of items by 1
	- For stackable items he can click on top right quarter or on bottom right quarter to individually set up max and min levels of an item
	- He can use Shift + left click and Shift + right click to increase or decrease amount fast, in this mode switches between max stack size, half stack size and zero.
- This way user can set up which parts of his inventory he want's to unload and which parts he want to load with which items, and how many items he wants in each particular slot and also if a should be cleared or filled and to how much extend.
- When this all done user switches back to "Operational" mode
- In this mode main function of wardrobe is "Transfer items". The same function is called with "Transfer items" button from UI in "Operational" mode or with just right clicking Wardrobe without opening it's UI
	- In setup mode checkbox "Enable right click" should enable/disable right clicking.
- "Transfer items" function should iterate over all slots and...
	- 1) Remove unwanted or excess items - items from 'unload' slots
	- 2) Put requested items into 'load' slots. 
	- 3) If any items was removed, in should be unloaded to some chest, using following rules:
		- Check if any slot in any setup loads this item (or in blue mode), then first try to unload to input chests.
			- If any input chest have same item and less that 2 stacks of that item - unload item to that input chest first.
		- If there are too many items in input chest (more that 2 stacks) or no slots load that item - then unload item to output chest.
	- 4) If there is not enough space to unload an item - keep it where it was and continue with other items.
- If UI is opened in "Operational" mode then all slots that have load/unload actions should
	- Have same border as they had during setup
	- Have background highlight
		- Green - if loading will happen and there are sufficient items do fill this slot
		- Yellow - not enough items to fill that slot
		- Red cross - the whole slot will be unloaded
	- If there is not enough space for unloading at the bottom of UI should be shown "Output chest is full!"
- If user clicks on an inventory slot in "Operational" mode that only that slot operation should be performed. 
	- User can also press mouse button and then move mouse to trigger multiple inventory slots to do it's operation this way.
- All operations is done immediately (as fast as possible).
- All operations should be done on server and be safe - not loosing/deleting any item and not dublicating any item is top priority in all cases.
- All unexpected errors (not described above) should be printed in red to that player's chat and to bottom text message.
