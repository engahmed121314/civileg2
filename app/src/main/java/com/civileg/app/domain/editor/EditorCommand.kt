package com.civileg.app.domain.editor

import com.civileg.app.domain.entities.FrameMaterialType
import com.civileg.app.domain.entities.FrameMember
import com.civileg.app.domain.entities.FrameMemberType
import com.civileg.app.domain.entities.FrameNode
import com.civileg.app.domain.entities.MemberLoad
import com.civileg.app.domain.entities.NodalLoad
import com.civileg.app.domain.entities.SupportType
import com.civileg.app.viewmodel.FrameAnalysisViewModel

/** Interface for undoable editor commands */
interface EditorCommand {
    fun execute()
    fun undo()
    val description: String
}

class AddNodeCommand(
    private val viewModel: FrameAnalysisViewModel,
    private val x: Double,
    private val y: Double,
    private val support: SupportType = SupportType.Free
) : EditorCommand {
    var resultingNodeId: Int = -1
    override val description = "إضافة عقدة عند ($x, $y)"

    override fun execute() {
        viewModel.addNode(x, y, support)
        resultingNodeId = viewModel.nodes.value?.maxOfOrNull { it.id } ?: -1
    }

    override fun undo() {
        if (resultingNodeId >= 0) {
            viewModel.removeNode(resultingNodeId)
        }
    }
}

class MoveNodeCommand(
    private val viewModel: FrameAnalysisViewModel,
    private val nodeId: Int,
    private val fromX: Double,
    private val fromY: Double,
    private val toX: Double,
    private val toY: Double,
    private val support: SupportType
) : EditorCommand {
    override val description = "نقل عقدة $nodeId من ($fromX, $fromY) إلى ($toX, $toY)"

    override fun execute() {
        viewModel.updateNode(nodeId, toX, toY, support)
    }

    override fun undo() {
        viewModel.updateNode(nodeId, fromX, fromY, support)
    }
}

class ChangeSupportCommand(
    private val viewModel: FrameAnalysisViewModel,
    private val nodeId: Int,
    private val x: Double,
    private val y: Double,
    private val fromSupport: SupportType,
    private val toSupport: SupportType
) : EditorCommand {
    override val description = "تغيير ارتكاز عقدة $nodeId"

    override fun execute() {
        viewModel.updateNode(nodeId, x, y, toSupport)
    }

    override fun undo() {
        viewModel.updateNode(nodeId, x, y, fromSupport)
    }
}

class AddMemberCommand(
    private val viewModel: FrameAnalysisViewModel,
    private val nodeI: Int,
    private val nodeJ: Int,
    private val materialType: FrameMaterialType,
    private val memberType: FrameMemberType
) : EditorCommand {
    var resultingMemberId: Int = -1
    override val description = "إضافة عضو من العقدة $nodeI إلى $nodeJ"

    override fun execute() {
        viewModel.addMember(nodeI, nodeJ, materialType, memberType)
        resultingMemberId = viewModel.members.value?.maxOfOrNull { it.id } ?: -1
    }

    override fun undo() {
        if (resultingMemberId >= 0) {
            viewModel.removeMember(resultingMemberId)
        }
    }
}

class DeleteNodeCommand(
    private val viewModel: FrameAnalysisViewModel,
    private val node: FrameNode,
    private val connectedMembers: List<FrameMember>
) : EditorCommand {
    override val description = "حذف العقدة ${node.id}"

    override fun execute() {
        viewModel.removeNode(node.id)
    }

    override fun undo() {
        viewModel.addNode(node.x, node.y, node.support)
        for (m in connectedMembers) {
            viewModel.addMember(m.nodeI, m.nodeJ, m.materialType, m.memberType, m.concreteSection, m.steelSectionName, m.name)
        }
    }
}

class DeleteMemberCommand(
    private val viewModel: FrameAnalysisViewModel,
    private val member: FrameMember
) : EditorCommand {
    override val description = "حذف العضو ${member.id}"

    override fun execute() {
        viewModel.removeMember(member.id)
    }

    override fun undo() {
        viewModel.addMember(member.nodeI, member.nodeJ, member.materialType, member.memberType, member.concreteSection, member.steelSectionName, member.name)
    }
}

class AddNodalLoadCommand(
    private val viewModel: FrameAnalysisViewModel,
    private val load: NodalLoad
) : EditorCommand {
    override val description = "إضافة حمولة على العقدة ${load.nodeId}"

    override fun execute() {
        viewModel.addNodalLoad(load.nodeId, load.fx, load.fy, load.mz, load.loadCase)
    }

    override fun undo() {
        val currentLoads = viewModel.nodalLoads.value ?: emptyList()
        val index = currentLoads.indexOfLast { it.nodeId == load.nodeId && it.fx == load.fx && it.fy == load.fy && it.mz == load.mz }
        if (index >= 0) {
            viewModel.removeNodalLoad(index)
        }
    }
}

class AddMemberLoadCommand(
    private val viewModel: FrameAnalysisViewModel,
    private val load: MemberLoad
) : EditorCommand {
    override val description = "إضافة حمولة على العضو ${load.memberId}"

    override fun execute() {
        viewModel.addMemberLoad(load.memberId, load.loadType, load.value, load.position, load.loadCase)
    }

    override fun undo() {
        val currentLoads = viewModel.memberLoads.value ?: emptyList()
        val index = currentLoads.indexOfLast { it.memberId == load.memberId && it.value == load.value && it.loadType == load.loadType }
        if (index >= 0) {
            viewModel.removeMemberLoad(index)
        }
    }
}
