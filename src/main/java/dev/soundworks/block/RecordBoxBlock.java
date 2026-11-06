package dev.soundworks.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Standing records by default; sneak while placing = lying. Sneak + empty-hand right click switches. */
public class RecordBoxBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty STANDING = BooleanProperty.create("standing");

	public RecordBoxBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STANDING, true));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
		b.add(FACING, STANDING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return defaultBlockState()
			.setValue(FACING, ctx.getHorizontalDirection().getOpposite())
			.setValue(STANDING, !ctx.isSecondaryUseActive());
	}

	@Override
	public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player.isShiftKeyDown()) {
			if (!level.isClientSide()) level.setBlock(pos, state.cycle(STANDING), Block.UPDATE_ALL);
			return InteractionResult.SUCCESS;
		}
		if (!level.isClientSide()) {
			MenuProvider menu = state.getMenuProvider(level, pos);
			if (menu != null) player.openMenu(menu);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new RecordBoxBlockEntity(pos, state);
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
		if (level.getBlockEntity(pos) instanceof Container c) Containers.dropContents(level, pos, c);
		super.affectNeighborsAfterRemoval(state, level, pos, moved);
	}
}
